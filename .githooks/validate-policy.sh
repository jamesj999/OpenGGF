#!/bin/sh

set -eu

GITHUB_FILE_SIZE_LIMIT_BYTES=100000000
TRACE_COMPRESSION_THRESHOLD_BYTES=1048576

die() {
    echo "policy: $*" >&2
    exit 1
}

note() {
    echo "policy: $*" >&2
}

current_branch() {
    git symbolic-ref --quiet --short HEAD 2>/dev/null || echo "HEAD"
}

is_merge_in_progress() {
    git rev-parse -q --verify MERGE_HEAD >/dev/null 2>&1
}

merge_head_oid() {
    git rev-parse -q --verify MERGE_HEAD 2>/dev/null || true
}

master_tip_oid() {
    git rev-parse -q --verify refs/heads/master 2>/dev/null || true
}

is_merge_from_master() {
    merge_oid=$(merge_head_oid)
    master_oid=$(master_tip_oid)
    [ -n "$merge_oid" ] && [ -n "$master_oid" ] && [ "$merge_oid" = "$master_oid" ]
}

staged_files() {
    git diff --cached --name-only --diff-filter=ACMR
}

commit_files() {
    git diff-tree --root --no-commit-id --name-only --diff-filter=ACMR -r "$1"
}

staged_blob_size() {
    git cat-file -s ":$1" 2>/dev/null || true
}

commit_blob_size() {
    git cat-file -s "$1:$2" 2>/dev/null || true
}

has_exact() {
    files=$1
    needle=$2
    old_ifs=$IFS
    IFS='
'
    for path in $files; do
        if [ "$path" = "$needle" ]; then
            IFS=$old_ifs
            return 0
        fi
    done
    IFS=$old_ifs
    return 1
}

has_prefix() {
    files=$1
    prefix=$2
    old_ifs=$IFS
    IFS='
'
    for path in $files; do
        case "$path" in
            "$prefix"*)
                IFS=$old_ifs
                return 0
                ;;
        esac
    done
    IFS=$old_ifs
    return 1
}

trailer_value() {
    key=$1
    message=$2
    printf '%s\n' "$message" | git interpret-trailers --parse | awk -v key="$key" '
        index($0, key ":") == 1 {
            value = substr($0, length(key) + 3)
        }
        END {
            if (value != "") {
                print value
            }
        }
    '
}

decision_kind() {
    value=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
    case "$value" in
        updated|updated\ *|updated:*|updated-*)
            echo "updated"
            ;;
        n/a|n/a\ *|n/a:*|n/a-*)
            echo "na"
            ;;
        *)
            echo "invalid"
            ;;
    esac
}

print_commit_template() {
    cat <<'EOF' >&2
Use these trailers on non-master branch commits:

Changelog: updated|n/a
Guide: updated|n/a
Known-Discrepancies: updated|n/a
S3K-Known-Discrepancies: updated|n/a
Agent-Docs: updated|n/a
Configuration-Docs: updated|n/a
Skills: updated|n/a

If a trailer says `updated`, the matching files must be staged in the same commit.
EOF
}

append_error() {
    if [ -z "${ERRORS:-}" ]; then
        ERRORS="- $1"
    else
        ERRORS="${ERRORS}
- $1"
    fi
}

validate_file_size_policy() {
    files=$1
    mode=$2
    commit=${3:-}
    old_ifs=$IFS
    IFS='
'
    for path in $files; do
        if [ "$mode" = "commit" ]; then
            size=$(commit_blob_size "$commit" "$path")
        else
            size=$(staged_blob_size "$path")
        fi
        if [ -z "$size" ]; then
            continue
        fi

        case "$path" in
            */aux_state*.jsonl|*/physics*.csv)
                if [ "$size" -ge "$TRACE_COMPRESSION_THRESHOLD_BYTES" ]; then
                    append_error "\`$path\` is an uncompressed trace payload (${size} bytes). Run \`tools/traces/compress-traces.ps1\` and commit the \`.gz\` file instead."
                fi
                ;;
        esac

        if [ "$size" -ge "$GITHUB_FILE_SIZE_LIMIT_BYTES" ]; then
            append_error "\`$path\` is ${size} bytes; GitHub rejects files >= ${GITHUB_FILE_SIZE_LIMIT_BYTES} bytes."
        fi
    done
    IFS=$old_ifs
}

validate_exact_trailer() {
    message=$1
    files=$2
    key=$3
    path=$4
    label=$5

    value=$(trailer_value "$key" "$message")
    if [ -z "$value" ]; then
        append_error "Missing \`$key\` trailer."
        return
    fi

    kind=$(decision_kind "$value")
    changed=1
    if has_exact "$files" "$path"; then
        changed=0
    fi

    case "$kind" in
        updated)
            if [ "$changed" -ne 0 ]; then
                append_error "\`$key\` says updated, but \`$label\` is not staged."
            fi
            ;;
        na)
            if [ "$changed" -eq 0 ]; then
                append_error "\`$key\` says n/a, but \`$label\` is staged."
            fi
            ;;
        *)
            append_error "\`$key\` must start with \`updated\` or \`n/a\`."
            ;;
    esac
}

validate_prefix_trailer() {
    message=$1
    files=$2
    key=$3
    prefix=$4
    label=$5

    value=$(trailer_value "$key" "$message")
    if [ -z "$value" ]; then
        append_error "Missing \`$key\` trailer."
        return
    fi

    kind=$(decision_kind "$value")
    changed=1
    if has_prefix "$files" "$prefix"; then
        changed=0
    fi

    case "$kind" in
        updated)
            if [ "$changed" -ne 0 ]; then
                append_error "\`$key\` says updated, but \`$label\` has no staged changes."
            fi
            ;;
        na)
            if [ "$changed" -eq 0 ]; then
                append_error "\`$key\` says n/a, but \`$label\` has staged changes."
            fi
            ;;
        *)
            append_error "\`$key\` must start with \`updated\` or \`n/a\`."
            ;;
    esac
}

validate_agent_docs_trailer() {
    message=$1
    files=$2
    key="Agent-Docs"

    value=$(trailer_value "$key" "$message")
    if [ -z "$value" ]; then
        append_error "Missing \`$key\` trailer."
        return
    fi

    kind=$(decision_kind "$value")
    agents_changed=1
    claude_changed=1
    if has_exact "$files" "AGENTS.md"; then
        agents_changed=0
    fi
    if has_exact "$files" "CLAUDE.md"; then
        claude_changed=0
    fi

    case "$kind" in
        updated)
            if [ "$agents_changed" -ne 0 ] || [ "$claude_changed" -ne 0 ]; then
                append_error "\`Agent-Docs\` says updated, but both \`AGENTS.md\` and \`CLAUDE.md\` must be staged together."
            fi
            ;;
        na)
            if [ "$agents_changed" -eq 0 ] || [ "$claude_changed" -eq 0 ]; then
                append_error "\`Agent-Docs\` says n/a, but agent docs are staged."
            fi
            ;;
        *)
            append_error "\`Agent-Docs\` must start with \`updated\` or \`n/a\`."
            ;;
    esac
}

validate_skills_trailer() {
    message=$1
    files=$2
    key="Skills"

    value=$(trailer_value "$key" "$message")
    if [ -z "$value" ]; then
        append_error "Missing \`$key\` trailer."
        return
    fi

    kind=$(decision_kind "$value")
    agents_changed=1
    claude_changed=1
    if has_prefix "$files" ".agents/skills/"; then
        agents_changed=0
    fi
    if has_prefix "$files" ".claude/skills/"; then
        claude_changed=0
    fi

    case "$kind" in
        updated)
            if [ "$agents_changed" -ne 0 ] || [ "$claude_changed" -ne 0 ]; then
                append_error "\`Skills\` says updated, but both \`.agents/skills/\` and \`.claude/skills/\` must have staged changes."
            fi
            ;;
        na)
            if [ "$agents_changed" -eq 0 ] || [ "$claude_changed" -eq 0 ]; then
                append_error "\`Skills\` says n/a, but skill changes are staged."
            fi
            ;;
        *)
            append_error "\`Skills\` must start with \`updated\` or \`n/a\`."
            ;;
    esac
}

validate_non_master_commit_message() {
    message=$1
    files=$2
    ERRORS=""

    validate_file_size_policy "$files" staged
    validate_exact_trailer "$message" "$files" "Changelog" "CHANGELOG.md" "CHANGELOG.md"
    validate_prefix_trailer "$message" "$files" "Guide" "docs/guide/" "docs/guide/"
    validate_exact_trailer "$message" "$files" "Known-Discrepancies" "docs/KNOWN_DISCREPANCIES.md" "docs/KNOWN_DISCREPANCIES.md"
    validate_exact_trailer "$message" "$files" "S3K-Known-Discrepancies" "docs/S3K_KNOWN_DISCREPANCIES.md" "docs/S3K_KNOWN_DISCREPANCIES.md"
    validate_agent_docs_trailer "$message" "$files"
    validate_exact_trailer "$message" "$files" "Configuration-Docs" "CONFIGURATION.md" "CONFIGURATION.md"
    validate_skills_trailer "$message" "$files"

    if [ -n "$ERRORS" ]; then
        note "non-master branch commits must declare the documentation/discrepancy policy explicitly."
        echo "$ERRORS" >&2
        print_commit_template
        exit 1
    fi
}

validate_merge_into_develop() {
    branch=$(current_branch)
    if [ "$branch" != "develop" ]; then
        return 0
    fi

    if ! is_merge_in_progress; then
        return 0
    fi

    if is_merge_from_master; then
        return 0
    fi

    files=$(staged_files)
    if ! has_exact "$files" "README.md"; then
        die "merging a non-master branch into develop requires a staged README.md update summarizing the branch change."
    fi
}

prepare_commit_message() {
    msg_file=$1
    source=${2:-}

    if [ "$(current_branch)" = "master" ]; then
        return 0
    fi

    case "$source" in
        merge|squash)
            return 0
            ;;
    esac

    if is_merge_in_progress; then
        return 0
    fi

    message=$(cat "$msg_file")
    parsed=$(printf '%s\n' "$message" | git interpret-trailers --parse)

    append_block=""
    for key in \
        "Changelog" \
        "Guide" \
        "Known-Discrepancies" \
        "S3K-Known-Discrepancies" \
        "Agent-Docs" \
        "Configuration-Docs" \
        "Skills"
    do
        if ! printf '%s\n' "$parsed" | awk -v key="$key" 'index($0, key ":") == 1 { found = 1 } END { exit !found }'; then
            if [ -z "$append_block" ]; then
                append_block="$key: TODO"
            else
                append_block="${append_block}
$key: TODO"
            fi
        fi
    done

    if [ -z "$append_block" ]; then
        return 0
    fi

    {
        printf '\n'
        printf '%s\n' "$append_block"
    } >>"$msg_file"
}

validate_commit_msg_hook() {
    msg_file=$1
    branch=$(current_branch)

    if [ "$branch" = "master" ]; then
        return 0
    fi

    if is_merge_in_progress; then
        validate_merge_into_develop
        return 0
    fi

    message=$(cat "$msg_file")
    files=$(staged_files)
    validate_non_master_commit_message "$message" "$files"
}

validate_ci_pr() {
    base_sha=$1
    head_sha=$2
    base_ref=$3
    head_ref=$4

    if [ "$base_ref" != "develop" ]; then
        return 0
    fi

    range_files=$(git diff --name-only --diff-filter=ACMR "$base_sha...$head_sha")

    if [ "$head_ref" != "master" ] && ! has_exact "$range_files" "README.md"; then
        die "PRs from non-master branches into develop must update README.md with a brief branch summary."
    fi

    if [ "$head_ref" = "master" ]; then
        return 0
    fi

    for commit in $(git rev-list --reverse --no-merges "$base_sha..$head_sha"); do
        message=$(git show -s --format=%B "$commit")
        files=$(commit_files "$commit")
        ERRORS=""

        validate_file_size_policy "$files" commit "$commit"
        validate_exact_trailer "$message" "$files" "Changelog" "CHANGELOG.md" "CHANGELOG.md"
        validate_prefix_trailer "$message" "$files" "Guide" "docs/guide/" "docs/guide/"
        validate_exact_trailer "$message" "$files" "Known-Discrepancies" "docs/KNOWN_DISCREPANCIES.md" "docs/KNOWN_DISCREPANCIES.md"
        validate_exact_trailer "$message" "$files" "S3K-Known-Discrepancies" "docs/S3K_KNOWN_DISCREPANCIES.md" "docs/S3K_KNOWN_DISCREPANCIES.md"
        validate_agent_docs_trailer "$message" "$files"
        validate_exact_trailer "$message" "$files" "Configuration-Docs" "CONFIGURATION.md" "CONFIGURATION.md"
        validate_skills_trailer "$message" "$files"

        if [ -n "$ERRORS" ]; then
            note "commit $commit violates the non-master branch documentation policy."
            echo "$ERRORS" >&2
            print_commit_template
            exit 1
        fi
    done
}

mode=${1:-}

case "$mode" in
    prepare-commit-msg)
        prepare_commit_message "$2" "${3:-}"
        ;;
    commit-msg)
        validate_commit_msg_hook "$2"
        ;;
    pre-merge-commit)
        validate_merge_into_develop
        ;;
    ci-pr)
        validate_ci_pr "$2" "$3" "$4" "$5"
        ;;
    *)
        die "usage: $0 {prepare-commit-msg|commit-msg|pre-merge-commit|ci-pr} ..."
        ;;
esac
