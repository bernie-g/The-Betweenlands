package thebetweenlands.client.render.shader.effect;

import java.io.StringWriter;
import java.nio.FloatBuffer;

import org.apache.commons.io.IOUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;

public abstract class DeferredEffect {
	//Clear colors
	private float cr, cg, cb, ca;

	//Buffers to restore matrices
	private FloatBuffer modelview = GLAllocation.createDirectFloatBuffer(16);
	private FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);
	private int prevShader = 0;

	//Additional stages
	private DeferredEffect[] stages;

	private int shaderProgramID = -1;
	private int diffuseSamplerUniformID = -1;
	private int texelSizeUniformID = -1;

	/**
	 * Initializes the effect. Requires an OpenGL context to work
	 */
	public final DeferredEffect init() {
		this.initShaders();
		this.stages = this.getStages();
		if(this.stages != null && this.stages.length > 0) {
			for(DeferredEffect stage : this.stages) {
				stage.init();
			}
		}
		return this;
	}

	/**
	 * Sets the default background color
	 * @param r Red
	 * @param g Green
	 * @param b Blue
	 * @param a Alpha
	 * @return
	 */
	public final DeferredEffect setBackgroundColor(float r, float g, float b, float a) {
		this.cr = r;
		this.cg = g;
		this.cb = b;
		this.ca = a;
		return this;
	}

	/**
	 * Deletes all shaders and frees memory
	 */
	public final void delete() {
		ARBShaderObjects.glDeleteObjectARB(this.shaderProgramID);
		if(this.stages != null && this.stages.length > 0) {
			for(DeferredEffect stage : this.stages) {
				stage.delete();
			}
		}
		this.deleteEffect();
	}

	/**
	 * Applies the effect to a texture and renders it to the destination FBO.
	 * Handles perspective only. Blending, texture mode, lighting etc. have to be
	 * handled manually.
	 * @param src Source texture ID
	 * @param dst Destination FBO
	 * @param prev Previous FBO
	 */
	public final void apply(int src, Framebuffer dst, Framebuffer blitBuffer, Framebuffer prev) {
		if(this.shaderProgramID == -1 || dst == null) return;

		this.prevShader = 0;

		ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft(), Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
		if(prev != null) {
			//Backup matrices
			GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
			GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
			this.prevShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

			//Set up 2D matrices
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GL11.glOrtho(0.0D, Math.ceil(scaledResolution.getScaledWidth_double()), Math.ceil(scaledResolution.getScaledHeight_double()), 0.0D, 1000.0D, 3000.0D);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glLoadIdentity();
			GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

			//Backup previous shader
			GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		}

		//Bind destination FBO
		dst.bindFramebuffer(false);

		//Clear buffers
		GL11.glClearColor(this.cr, this.cg, this.cb, this.ca);
		GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

		//Calculate render size
		double renderWidth = Math.floor((double)dst.framebufferTextureWidth / (double)Minecraft.getMinecraft().displayWidth * (double)scaledResolution.getScaledWidth());
		double renderHeight = Math.floor((double)dst.framebufferTextureHeight / (double)Minecraft.getMinecraft().displayHeight * (double)scaledResolution.getScaledHeight());

		//Use shader
		ARBShaderObjects.glUseProgramObjectARB(this.shaderProgramID);

		//Upload sampler uniform (src texture ID)
		ARBShaderObjects.glUniform1iARB(this.diffuseSamplerUniformID, 0);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, src);

		//Upload texel size uniform
		if(this.texelSizeUniformID != -1) {
			FloatBuffer texelSizeBuffer = BufferUtils.createFloatBuffer(2);
			texelSizeBuffer.position(0);
			texelSizeBuffer.put(1.0F / (float)Minecraft.getMinecraft().displayWidth);
			texelSizeBuffer.put(1.0F / (float)Minecraft.getMinecraft().displayHeight);
			texelSizeBuffer.flip();
			ARBShaderObjects.glUniform2ARB(this.texelSizeUniformID, texelSizeBuffer);
		}

		//Render texture
		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glTexCoord2d(0, 1);
		GL11.glVertex2d(0, 0);
		GL11.glTexCoord2d(0, 0);
		GL11.glVertex2d(0, renderHeight);
		GL11.glTexCoord2d(1, 0);
		GL11.glVertex2d(renderWidth, renderHeight);
		GL11.glTexCoord2d(1, 0);
		GL11.glVertex2d(renderWidth, renderHeight);
		GL11.glTexCoord2d(1, 1);
		GL11.glVertex2d(renderWidth, 0);
		GL11.glTexCoord2d(0, 1);
		GL11.glVertex2d(0, 0);
		GL11.glEnd();

		//Apply additional stages
		if(blitBuffer != null && this.stages != null && this.stages.length > 0) {
			for(DeferredEffect stage : this.stages) {
				//Render to blit buffer
				stage.apply(dst.framebufferTexture, blitBuffer, dst, null);

				//Render from blit buffer to destination buffer
				dst.bindFramebuffer(false);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, blitBuffer.framebufferTexture);
				GL11.glBegin(GL11.GL_TRIANGLES);
				GL11.glTexCoord2d(0, 1);
				GL11.glVertex2d(0, 0);
				GL11.glTexCoord2d(0, 0);
				GL11.glVertex2d(0, renderHeight);
				GL11.glTexCoord2d(1, 0);
				GL11.glVertex2d(renderWidth, renderHeight);
				GL11.glTexCoord2d(1, 0);
				GL11.glVertex2d(renderWidth, renderHeight);
				GL11.glTexCoord2d(1, 1);
				GL11.glVertex2d(renderWidth, 0);
				GL11.glTexCoord2d(0, 1);
				GL11.glVertex2d(0, 0);
				GL11.glEnd();
			}
		}

		//Restore previous shader (or bind default)
		ARBShaderObjects.glUseProgramObjectARB(this.prevShader);

		if(prev != null) {
			//Restore matrices
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glLoadIdentity();
			GL11.glLoadMatrix(this.projection);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glLoadIdentity();
			GL11.glLoadMatrix(this.modelview);

			//Bind previous FBO
			prev.bindFramebuffer(true);
		}
	}

	/**
	 * Initializes the shaders
	 */
	private void initShaders() {
		if(this.shaderProgramID == -1) {
			this.shaderProgramID = ARBShaderObjects.glCreateProgramObjectARB();
			int vertexShaderID = -1;
			int fragmentShaderID = -1;
			try {
				ResourceLocation[] shaderLocations = this.getShaders();
				String[] shaders = new String[2];
				for(int i = 0; i < 2; i++) {
					StringWriter strBuf = new StringWriter();
					IOUtils.copy(Minecraft.getMinecraft().getResourceManager().getResource(shaderLocations[i]).getInputStream(), strBuf, "UTF-8");
					shaders[i] = strBuf.toString();
				}
				vertexShaderID = this.createShader(shaders[0], ARBVertexShader.GL_VERTEX_SHADER_ARB);
				fragmentShaderID = this.createShader(shaders[1], ARBFragmentShader.GL_FRAGMENT_SHADER_ARB);
			} catch(Exception ex) {
				this.shaderProgramID = -1;
				vertexShaderID = -1;
				fragmentShaderID = -1;

				throw new RuntimeException("Error creating shader", ex);
			}
			if(this.shaderProgramID != -1 && vertexShaderID != -1 && fragmentShaderID != -1) {
				//Attach and link vertex and fragment shader to shader program
				ARBShaderObjects.glAttachObjectARB(this.shaderProgramID, vertexShaderID);
				ARBShaderObjects.glAttachObjectARB(this.shaderProgramID, fragmentShaderID);
				ARBShaderObjects.glLinkProgramARB(this.shaderProgramID);

				//Check for errors
				if (ARBShaderObjects.glGetObjectParameteriARB(this.shaderProgramID, ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB) == GL11.GL_FALSE) {
					throw new RuntimeException("Error creating shader: " + getLogInfo(this.shaderProgramID));
				}
				ARBShaderObjects.glValidateProgramARB(this.shaderProgramID);
				if (ARBShaderObjects.glGetObjectParameteriARB(this.shaderProgramID, ARBShaderObjects.GL_OBJECT_VALIDATE_STATUS_ARB) == GL11.GL_FALSE) {
					throw new RuntimeException("Error creating shader: " + getLogInfo(this.shaderProgramID));
				}

				//Delete vertex and fragment shader
				ARBShaderObjects.glDeleteObjectARB(vertexShaderID);
				ARBShaderObjects.glDeleteObjectARB(fragmentShaderID);

				//Get uniforms
				this.diffuseSamplerUniformID = ARBShaderObjects.glGetUniformLocationARB(this.shaderProgramID, "s_diffuse");
				this.texelSizeUniformID = ARBShaderObjects.glGetUniformLocationARB(this.shaderProgramID, "v_oneTexel");

				//Unbind shader
				ARBShaderObjects.glUseProgramObjectARB(0);
			} else if(this.shaderProgramID != -1) {
				ARBShaderObjects.glDeleteObjectARB(this.shaderProgramID);
			}
		}
	}

	/**
	 * Compiles and creates a shader from the shader code.
	 * @param shaderCode
	 * @param shaderType
	 * @return
	 * @throws Exception
	 */
	private static int createShader(String shaderCode, int shaderType) throws Exception {
		int shader = 0;
		try {
			shader = ARBShaderObjects.glCreateShaderObjectARB(shaderType);
			if(shader == 0) {
				return 0;
			}
			ARBShaderObjects.glShaderSourceARB(shader, shaderCode);
			ARBShaderObjects.glCompileShaderARB(shader);
			if (ARBShaderObjects.glGetObjectParameteriARB(shader, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB) == GL11.GL_FALSE) {
				throw new RuntimeException("Error creating shader: " + getLogInfo(shader));
			}
			return shader;
		} catch(Exception exc) {
			ARBShaderObjects.glDeleteObjectARB(shader);
			throw exc;
		}
	}
	private static String getLogInfo(int obj) {
		return ARBShaderObjects.glGetInfoLogARB(obj, ARBShaderObjects.glGetObjectParameteriARB(obj, ARBShaderObjects.GL_OBJECT_INFO_LOG_LENGTH_ARB));
	}

	/**
	 * Returns the shader code. [0] = vertex shader, [1] = fragment shader
	 * @return
	 */
	protected abstract ResourceLocation[] getShaders();

	/**
	 * Uploads any additional uniforms
	 */
	protected void uploadUniforms() {}

	/**
	 * Returns additional stages
	 * @return
	 */
	protected DeferredEffect[] getStages() { return null; }

	/**
	 * Used to delete additional things and free memory
	 */
	protected void deleteEffect() {}
}
