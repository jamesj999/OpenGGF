package uk.co.jamesj999.sonic.graphics;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class GLCommandGroup implements GLCommandable {
	private int drawMethod;
	private List<GLCommand> commands;

	public GLCommandGroup(int drawMethod, List<GLCommand> commands) {
		this.drawMethod = drawMethod;
		this.commands = commands;
	}

    public void execute(int cameraX, int cameraY, int cameraWidth, int cameraHeight) {
        if (!commands.isEmpty()) {
            GLCommand.BlendType blendMode = commands.get(0).getBlendMode();
            if (blendMode == GLCommand.BlendType.SOLID) {
                glDisable(GL_BLEND);
            } else {
                glEnable(GL_BLEND);
                glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            }
        }
        GLCommand.setInGroup(true);
        glBegin(drawMethod);
        for(GLCommand command : commands) {
            command.execute(cameraX, cameraY, cameraWidth, cameraHeight);
        }
        glEnd();
        GLCommand.setInGroup(false);
    }
}
