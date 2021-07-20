package com.silenoids.walldream.rendering;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.silenoids.walldream.R;
import com.silenoids.walldream.helper.ObjectFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CubeRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = CubeRenderer.class.getName();

    private float[] modelMatrix = new float[16];   //matrice di posizionamento
    private float[] viewMatrix = new float[16];    //matrice della view, o camera
    private float[] projectionMatrix = new float[16];  //matrice di proiezione da 3D a 2D
    private float[] mvpMatrix = new float[16];     //Matrice risultante che verrà passata allo shader
    private float[] lightModelMatrix = new float[16];  //copia della matrice del model per calcoli di luce

    // FloatBuffer per conservare lo stato del model data
    private final FloatBuffer mCubePositions;
    private final FloatBuffer mCubeColors;
    private final FloatBuffer mCubeNormals;

    // Variabili per il passaggio
    private int mMVPMatrixHandle;   //Transformation matrix.
    private int mMVMatrixHandle;   //modelview matrix
    private int mLightPosHandle;   //light position
    private int mPositionHandle;   //model position information
    private int mColorHandle;   //model color information
    private int mNormalHandle;   //model normal information

    // Varie size per gli elementi
    private final int mBytesPerFloat = 4;
    private final int mPositionDataSize = 3;
    private final int mColorDataSize = 4;
    private final int mNormalDataSize = 3;

    // light centered on the origin in model space. We need a 4th coordinate so we
    // can get translations to work when we multiply this by our transformation matrices.
    private final float[] mLightPosInModelSpace = new float[]{0.0f, 0.0f, 0.0f, 1.0f};
    //after transformation via model matrix
    private final float[] mLightPosInWorldSpace = new float[4];
    private final float[] mLightPosInEyeSpace = new float[4];

    // Handle per-vertex cube shading program and point program.
    private int mPerVertexProgramHandle;
    private int mPointProgramHandle;

    // Initialize the model data.
    public CubeRenderer() {
        ObjectFactory objectFactory = ObjectFactory.getInstance();
        // X, Y, Z
        final float[] cubePositionData = objectFactory.getCubePositionData();
        // R, G, B, A
        final float[] cubeColorData = objectFactory.getCubeColorData();
        // Normali ortogonali alle facce
        final float[] cubeNormalData = objectFactory.getCubeNormalData();

        // Inizializza i buffer
        mCubePositions = ByteBuffer.allocateDirect(cubePositionData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubePositions.put(cubePositionData).position(0);

        mCubeColors = ByteBuffer.allocateDirect(cubeColorData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubeColors.put(cubeColorData).position(0);

        mCubeNormals = ByteBuffer.allocateDirect(cubeNormalData.length * mBytesPerFloat).order(ByteOrder.nativeOrder()).asFloatBuffer();
        mCubeNormals.put(cubeNormalData).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glEnable(GLES20.GL_CULL_FACE);   // Abilita il culling per rimuovere le backface
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);  // Abilita depth testing

        // Definisci la matrice di vista
        final float[] eyeCoord = {0.0f, 0.0f, -0.5f};
        final float[] lookCoord = {0.0f, 0.0f, -0.5f};
        final float[] upCoord = {0.0f, 1.0f, 0.0f};
        Matrix.setLookAtM(viewMatrix, 0,
                eyeCoord[0], eyeCoord[1], eyeCoord[2],
                lookCoord[0], lookCoord[1], lookCoord[2],
                upCoord[0], upCoord[1], upCoord[2]);

        // Definisci e linka gli shader per il cubo
        final String cubeVertexShader = ObjectFactory.getInstance().readRawResource(R.raw.vertex_cube);
        final String cubeFragmentShader = ObjectFactory.getInstance().readRawResource(R.raw.fragment_cube);
        final int vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, cubeVertexShader);
        final int fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, cubeFragmentShader);
        mPerVertexProgramHandle = createAndLinkProgram(
                vertexShaderHandle,
                fragmentShaderHandle,
                new String[]{"a_Position", "a_Color", "a_Normal"});

        // Definisci e linka gli shader per il punto luce
        final String pointVertexShader = ObjectFactory.getInstance().readRawResource(R.raw.vertex_point);
        final String pointFragmentShader = ObjectFactory.getInstance().readRawResource(R.raw.fragment_point);
        final int pointVertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, pointVertexShader);
        final int pointFragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, pointFragmentShader);
        mPointProgramHandle = createAndLinkProgram(
                pointVertexShaderHandle,
                pointFragmentShaderHandle,
                new String[]{"a_Position"});
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Setta la viewport OpenGL di dimensioni uguali alla Surface
        GLES20.glViewport(0, 0, width, height);

        final float ratio = (float) width / height;
        final float left = -ratio;
        final float right = ratio;
        final float bottom = -1.0f;
        final float top = 1.0f;
        final float near = 1.0f;
        final float far = 10.0f;

        // Ricrea la matrice di poriezione con prospettiva basata sull'altezza
        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, near, far);
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Do a complete rotation every 10 seconds.
        long time = SystemClock.uptimeMillis() % 10000L;
        float angleInDegrees = (360.0f / 10000.0f) * ((int) time);

        // Set our per-vertex lighting program.
        GLES20.glUseProgram(mPerVertexProgramHandle);

        // Set program handles for cube drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_MVMatrix");
        mLightPosHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_LightPos");
        mPositionHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Color");
        mNormalHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Normal");

        // Calculate position of the light. Rotate and then push into the distance.
        Matrix.setIdentityM(lightModelMatrix, 0);
        Matrix.translateM(lightModelMatrix, 0, 0.0f, 0.0f, -5.0f);
        Matrix.rotateM(lightModelMatrix, 0, angleInDegrees, 0.0f, 1.0f, 0.0f);
        Matrix.translateM(lightModelMatrix, 0, 0.0f, 0.0f, 2.0f);

        Matrix.multiplyMV(mLightPosInWorldSpace, 0, lightModelMatrix, 0, mLightPosInModelSpace, 0);
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, viewMatrix, 0, mLightPosInWorldSpace, 0);

        // Draw some cubes.
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 4.0f, 0.0f, -7.0f);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 1.0f, 0.0f, 0.0f);
        drawCube();

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, -4.0f, 0.0f, -7.0f);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 0.0f, 1.0f, 0.0f);
        drawCube();

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0.0f, 4.0f, -7.0f);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 0.0f, 0.0f, 1.0f);
        drawCube();

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0.0f, -4.0f, -7.0f);
        drawCube();

        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0.0f, 0.0f, -5.0f);
        Matrix.rotateM(modelMatrix, 0, angleInDegrees, 1.0f, 1.0f, 0.0f);
        drawCube();

        // Draw a point to indicate the light.
        GLES20.glUseProgram(mPointProgramHandle);
        drawLight();
    }

    private void drawCube() {
        // Pass in the position information
        mCubePositions.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false,0, mCubePositions);

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        // Pass in the color information
        mCubeColors.position(0);
        GLES20.glVertexAttribPointer(mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false,0, mCubeColors);

        GLES20.glEnableVertexAttribArray(mColorHandle);
        // Pass in the normal information
        mCubeNormals.position(0);
        GLES20.glVertexAttribPointer(mNormalHandle, mNormalDataSize, GLES20.GL_FLOAT, false,0, mCubeNormals);

        GLES20.glEnableVertexAttribArray(mNormalHandle);
        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mvpMatrix, 0);
        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
        // Pass in the light position in eye space.
        GLES20.glUniform3f(mLightPosHandle, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]);
        // Draw the cube.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }

    private void drawLight() {
        final int pointMVPMatrixHandle = GLES20.glGetUniformLocation(mPointProgramHandle, "u_MVPMatrix");
        final int pointPositionHandle = GLES20.glGetAttribLocation(mPointProgramHandle, "a_Position");

        // Pass in the position.
        GLES20.glVertexAttrib3f(pointPositionHandle, mLightPosInModelSpace[0], mLightPosInModelSpace[1], mLightPosInModelSpace[2]);

        // Since we are not using a buffer object, disable vertex arrays for this attribute.
        GLES20.glDisableVertexAttribArray(pointPositionHandle);

        // Pass in the transformation matrix.
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, lightModelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(pointMVPMatrixHandle, 1, false, mvpMatrix, 0);

        // Draw the point.
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
    }

    private int compileShader(final int shaderType, final String shaderSource) {
        int shaderHandle = GLES20.glCreateShader(shaderType);

        if (shaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(shaderHandle, shaderSource);

            // Compile the shader.
            GLES20.glCompileShader(shaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderHandle));
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }

        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shaderHandle;
    }

    /**
     * Helper function to compile and link a program.
     *
     * @param vertexShaderHandle   An OpenGL handle to an already-compiled vertex shader.
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader.
     * @param attributes           Attributes that need to be bound to the program.
     * @return An OpenGL handle to the program.
     */
    private int createAndLinkProgram(final int vertexShaderHandle, final int fragmentShaderHandle, final String[] attributes) {
        int programHandle = GLES20.glCreateProgram();

        if (programHandle != 0) {
            GLES20.glAttachShader(programHandle, vertexShaderHandle);
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);

            // Bind attributes
            if (attributes != null) {
                final int size = attributes.length;
                for (int i = 0; i < size; i++) {
                    GLES20.glBindAttribLocation(programHandle, i, attributes[i]);
                }
            }

            // Link the two shaders together into a program.
            GLES20.glLinkProgram(programHandle);
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + GLES20.glGetProgramInfoLog(programHandle));
                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }

        return programHandle;
    }
}
