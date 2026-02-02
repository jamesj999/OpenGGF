package uk.co.jamesj999.sonic.graphics.compute;

import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Wrapper for OpenGL buffer objects (SSBOs and UBOs) used with compute shaders.
 * <p>
 * Shader Storage Buffer Objects (SSBOs) allow read/write access from compute shaders.
 * Uniform Buffer Objects (UBOs) are read-only but have faster access for small data.
 */
public class GpuBuffer {

    /**
     * Buffer type determines how the buffer is bound and accessed.
     */
    public enum Type {
        /** Shader Storage Buffer - read/write, larger capacity */
        SSBO(GL4.GL_SHADER_STORAGE_BUFFER),
        /** Uniform Buffer - read-only, faster for small data */
        UBO(GL4.GL_UNIFORM_BUFFER);

        final int glTarget;

        Type(int target) {
            this.glTarget = target;
        }
    }

    /**
     * Usage hint for buffer allocation.
     */
    public enum Usage {
        /** Data set once, used many times */
        STATIC(GL4.GL_STATIC_DRAW),
        /** Data modified occasionally, used many times */
        DYNAMIC(GL4.GL_DYNAMIC_DRAW),
        /** Data modified every frame */
        STREAM(GL4.GL_STREAM_DRAW),
        /** Data read back from GPU */
        READ(GL4.GL_STREAM_READ);

        final int glUsage;

        Usage(int usage) {
            this.glUsage = usage;
        }
    }

    private int bufferId;
    private final Type type;
    private int sizeBytes;

    /**
     * Create a new GPU buffer.
     *
     * @param gl        the OpenGL context
     * @param type      buffer type (SSBO or UBO)
     * @param sizeBytes initial size in bytes
     * @param usage     usage hint
     */
    public GpuBuffer(GL4 gl, Type type, int sizeBytes, Usage usage) {
        this.type = type;
        this.sizeBytes = sizeBytes;

        int[] buffers = new int[1];
        gl.glGenBuffers(1, buffers, 0);
        this.bufferId = buffers[0];

        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferData(type.glTarget, sizeBytes, null, usage.glUsage);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Create a buffer and initialize with integer data.
     *
     * @param gl    the OpenGL context
     * @param type  buffer type
     * @param data  initial data
     * @param usage usage hint
     */
    public GpuBuffer(GL4 gl, Type type, int[] data, Usage usage) {
        this.type = type;
        this.sizeBytes = data.length * Integer.BYTES;

        int[] buffers = new int[1];
        gl.glGenBuffers(1, buffers, 0);
        this.bufferId = buffers[0];

        IntBuffer buffer = IntBuffer.wrap(data);
        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferData(type.glTarget, sizeBytes, buffer, usage.glUsage);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Create a buffer and initialize with float data.
     *
     * @param gl    the OpenGL context
     * @param type  buffer type
     * @param data  initial data
     * @param usage usage hint
     */
    public GpuBuffer(GL4 gl, Type type, float[] data, Usage usage) {
        this.type = type;
        this.sizeBytes = data.length * Float.BYTES;

        int[] buffers = new int[1];
        gl.glGenBuffers(1, buffers, 0);
        this.bufferId = buffers[0];

        FloatBuffer buffer = FloatBuffer.wrap(data);
        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferData(type.glTarget, sizeBytes, buffer, usage.glUsage);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Get the OpenGL buffer ID.
     *
     * @return buffer ID
     */
    public int getBufferId() {
        return bufferId;
    }

    /**
     * Get the buffer type.
     *
     * @return SSBO or UBO
     */
    public Type getType() {
        return type;
    }

    /**
     * Get the buffer size in bytes.
     *
     * @return size in bytes
     */
    public int getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Bind this buffer to a binding point for use in shaders.
     * <p>
     * In GLSL, this corresponds to:
     * <pre>
     * layout(std430, binding = N) buffer BufferName { ... };
     * </pre>
     *
     * @param gl           the OpenGL context
     * @param bindingPoint the binding index (0, 1, 2, ...)
     */
    public void bindBase(GL4 gl, int bindingPoint) {
        gl.glBindBufferBase(type.glTarget, bindingPoint, bufferId);
    }

    /**
     * Bind a range of this buffer to a binding point.
     *
     * @param gl           the OpenGL context
     * @param bindingPoint the binding index
     * @param offset       byte offset into the buffer
     * @param size         size of the range in bytes
     */
    public void bindRange(GL4 gl, int bindingPoint, int offset, int size) {
        gl.glBindBufferRange(type.glTarget, bindingPoint, bufferId, offset, size);
    }

    /**
     * Upload integer data to the buffer.
     *
     * @param gl     the OpenGL context
     * @param data   data to upload
     * @param offset byte offset in the buffer
     */
    public void upload(GL4 gl, int[] data, int offset) {
        IntBuffer buffer = IntBuffer.wrap(data);
        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferSubData(type.glTarget, offset, (long) data.length * Integer.BYTES, buffer);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Upload float data to the buffer.
     *
     * @param gl     the OpenGL context
     * @param data   data to upload
     * @param offset byte offset in the buffer
     */
    public void upload(GL4 gl, float[] data, int offset) {
        FloatBuffer buffer = FloatBuffer.wrap(data);
        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferSubData(type.glTarget, offset, (long) data.length * Float.BYTES, buffer);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Upload byte data to the buffer.
     *
     * @param gl     the OpenGL context
     * @param data   data to upload
     * @param offset byte offset in the buffer
     */
    public void upload(GL4 gl, ByteBuffer data, int offset) {
        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferSubData(type.glTarget, offset, data.remaining(), data);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Read integer data back from the buffer.
     *
     * @param gl     the OpenGL context
     * @param dest   destination array
     * @param offset byte offset in the buffer
     */
    public void download(GL4 gl, int[] dest, int offset) {
        // OpenGL requires direct buffers - allocate one and copy to dest array
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(dest.length * Integer.BYTES)
                .order(java.nio.ByteOrder.nativeOrder());
        IntBuffer intBuffer = directBuffer.asIntBuffer();

        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glGetBufferSubData(type.glTarget, offset, (long) dest.length * Integer.BYTES, intBuffer);
        gl.glBindBuffer(type.glTarget, 0);

        // Copy from direct buffer to dest array
        intBuffer.rewind();
        intBuffer.get(dest);
    }

    /**
     * Read float data back from the buffer.
     *
     * @param gl     the OpenGL context
     * @param dest   destination array
     * @param offset byte offset in the buffer
     */
    public void download(GL4 gl, float[] dest, int offset) {
        // OpenGL requires direct buffers - allocate one and copy to dest array
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(dest.length * Float.BYTES)
                .order(java.nio.ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = directBuffer.asFloatBuffer();

        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glGetBufferSubData(type.glTarget, offset, (long) dest.length * Float.BYTES, floatBuffer);
        gl.glBindBuffer(type.glTarget, 0);

        // Copy from direct buffer to dest array
        floatBuffer.rewind();
        floatBuffer.get(dest);
    }

    /**
     * Resize the buffer. Existing data is discarded.
     *
     * @param gl        the OpenGL context
     * @param newSize   new size in bytes
     * @param usage     usage hint
     */
    public void resize(GL4 gl, int newSize, Usage usage) {
        this.sizeBytes = newSize;
        gl.glBindBuffer(type.glTarget, bufferId);
        gl.glBufferData(type.glTarget, newSize, null, usage.glUsage);
        gl.glBindBuffer(type.glTarget, 0);
    }

    /**
     * Delete the buffer and release GPU resources.
     *
     * @param gl the OpenGL context
     */
    public void cleanup(GL4 gl) {
        if (bufferId != 0) {
            gl.glDeleteBuffers(1, new int[]{bufferId}, 0);
            bufferId = 0;
        }
    }
}
