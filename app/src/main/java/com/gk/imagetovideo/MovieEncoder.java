package com.gk.imagetovideo;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.io.FileDescriptor;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class MovieEncoder {
    interface Progress { void changed(int currentFrame,int totalFrames); }
    private static final int FPS=30;
    private static final int EGL_RECORDABLE_ANDROID=0x3142;
    private MediaCodec codec;
    private MediaMuxer muxer;
    private Surface inputSurface;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface=EGL14.EGL_NO_SURFACE;
    private int program,positionLoc,texLoc,samplerLoc;
    private int muxTrack=-1;
    private boolean muxStarted=false;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int w,h;

    static void write(ContentResolver resolver,List<Uri> images,FileDescriptor fd,
            int w,int h,int seconds,AtomicBoolean cancelled,Progress progress) throws Exception {
        MovieEncoder e=new MovieEncoder();
        try {
            e.initialize(fd,w,h);
            int count=images.size()*seconds*FPS;
            int frame=0;
            for(Uri photo:images) {
                if(cancelled.get()) throw new InterruptedIOException("Export cancelled");
                Bitmap bitmap=decode(resolver,photo,w,h);
                if(bitmap==null) throw new Exception("Cannot read a selected photo");
                int texture=0;
                try {
                    texture=e.upload(bitmap);
                    float ar=(float)bitmap.getWidth()/bitmap.getHeight();
                    for(int f=0;f<seconds*FPS;f++) {
                        if(cancelled.get()) throw new InterruptedIOException("Export cancelled");
                        e.draw(texture,ar);
                        EGLExt.eglPresentationTimeANDROID(e.display,e.eglSurface,
                                frame*1000000000L/FPS);
                        if(!EGL14.eglSwapBuffers(e.display,e.eglSurface))
                            throw new Exception("Video render failed");
                        e.drain(false);
                        frame++;
                        if(frame%15==0 || frame==count) progress.changed(frame,count);
                    }
                } finally {
                    if(texture!=0) GLES20.glDeleteTextures(1,new int[]{texture},0);
                    bitmap.recycle();
                }
            }
            codecEnd(e);
        } finally { e.release(); }
    }

    private static void codecEnd(MovieEncoder e) throws Exception {
        e.codec.signalEndOfInputStream();
        if(!e.drain(true)) throw new Exception("Video encoder did not finish");
    }

    private static Bitmap decode(ContentResolver r,Uri u,int w,int h) throws Exception {
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        try(InputStream in=r.openInputStream(u)) {
            if(in==null) throw new Exception("Photo unavailable");
            BitmapFactory.decodeStream(in,null,opts);
        }
        if(opts.outWidth<=0 || opts.outHeight<=0) throw new Exception("Invalid image");
        int sample=1;
        int max=Math.max(w,h);
        while(opts.outWidth/sample>max*2 || opts.outHeight/sample>max*2) sample*=2;
        opts.inJustDecodeBounds=false;
        opts.inSampleSize=sample;
        Bitmap bmp;
        try(InputStream in=r.openInputStream(u)) {
            if(in==null) throw new Exception("Photo unavailable");
            bmp=BitmapFactory.decodeStream(in,null,opts);
        }
        if(bmp==null) throw new Exception("Cannot decode photo");
        int rotation=0;
        try(InputStream in=r.openInputStream(u)){
            if(in!=null){
                int orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
                if(orientation==ExifInterface.ORIENTATION_ROTATE_90) rotation=90;
                else if(orientation==ExifInterface.ORIENTATION_ROTATE_180) rotation=180;
                else if(orientation==ExifInterface.ORIENTATION_ROTATE_270) rotation=270;
            }
        }catch(Exception ignored){}
        if(rotation!=0){
            Matrix matrix=new Matrix(); matrix.postRotate(rotation);
            Bitmap rotated=Bitmap.createBitmap(bmp,0,0,bmp.getWidth(),bmp.getHeight(),matrix,true);
            if(rotated!=bmp) bmp.recycle();
            bmp=rotated;
        }
        return bmp;
    }

    private void initialize(FileDescriptor fd,int width,int height) throws Exception {
        w=width; h=height;
        MediaFormat fmt=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,w,h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE,(w>=1920?8000000:4000000));
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
        codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface=codec.createInputSurface();
        muxer=new MediaMuxer(fd,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        codec.start();

        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY) throw new Exception("EGL display unavailable");
        int[] ver=new int[2];
        if(!EGL14.eglInitialize(display,ver,0,ver,1)) throw new Exception("EGL initialization failed");
        int[] attrs={
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_ALPHA_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID,1,EGL14.EGL_NONE
        };
        EGLConfig[] configs=new EGLConfig[1];int[] n=new int[1];
        if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)||n[0]<1)
            throw new Exception("No suitable GPU video encoder configuration");
        int[] ctx={EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE};
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,ctx,0);
        eglSurface=EGL14.eglCreateWindowSurface(display,configs[0],inputSurface,new int[]{EGL14.EGL_NONE},0);
        if(context==EGL14.EGL_NO_CONTEXT || eglSurface==EGL14.EGL_NO_SURFACE ||
            !EGL14.eglMakeCurrent(display,eglSurface,eglSurface,context))
            throw new Exception("Could not prepare video rendering surface");

        String vertex="attribute vec4 aPosition; attribute vec2 aTexCoord; varying vec2 vTexCoord; void main(){gl_Position=aPosition;vTexCoord=aTexCoord;}";
        String fragment="precision mediump float; varying vec2 vTexCoord; uniform sampler2D sTexture; void main(){gl_FragColor=texture2D(sTexture,vTexCoord);}";
        int vs=shader(GLES20.GL_VERTEX_SHADER,vertex);
        int fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
        program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs);
        GLES20.glLinkProgram(program);
        int[] linked=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
        if(linked[0]!=GLES20.GL_TRUE) throw new Exception("OpenGL video shader link failed");
        positionLoc=GLES20.glGetAttribLocation(program,"aPosition");
        texLoc=GLES20.glGetAttribLocation(program,"aTexCoord");
        samplerLoc=GLES20.glGetUniformLocation(program,"sTexture");
    }

    private static int shader(int type,String source) throws Exception {
        int s=GLES20.glCreateShader(type);
        GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);
        int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new Exception("OpenGL shader compile failed");
        return s;
    }

    private int upload(Bitmap bitmap){
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
        return ids[0];
    }

    private void draw(int texture,float imageAspect){
        GLES20.glViewport(0,0,w,h);
        GLES20.glClearColor(0,0,0,1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        float outputAspect=(float)w/h;
        float sx=1f,sy=1f;
        if(imageAspect>outputAspect) sy=outputAspect/imageAspect;
        else sx=imageAspect/outputAspect;
        float[] vertices={
            -sx,-sy,0f,1f,
             sx,-sy,1f,1f,
            -sx, sy,0f,0f,
             sx, sy,1f,0f
        };
        FloatBuffer v=ByteBuffer.allocateDirect(vertices.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        v.put(vertices).position(0);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1i(samplerLoc,0);
        v.position(0);GLES20.glEnableVertexAttribArray(positionLoc);
        GLES20.glVertexAttribPointer(positionLoc,2,GLES20.GL_FLOAT,false,16,v);
        v.position(2);GLES20.glEnableVertexAttribArray(texLoc);
        GLES20.glVertexAttribPointer(texLoc,2,GLES20.GL_FLOAT,false,16,v);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(positionLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
        GLES20.glFinish();
    }

    private boolean drain(boolean end) throws Exception {
        int idle=0;
        for(;;){
            int index=codec.dequeueOutputBuffer(info,end?10000:0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER){
                if(!end) return false;
                if(++idle>300) return false;
            }else if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                if(muxStarted) throw new Exception("Unexpected video output change");
                muxTrack=muxer.addTrack(codec.getOutputFormat());
                muxer.start();muxStarted=true;
            }else if(index>=0){
                ByteBuffer buffer=codec.getOutputBuffer(index);
                if((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0) info.size=0;
                if(info.size>0){
                    if(!muxStarted || buffer==null) throw new Exception("Video file writer not ready");
                    buffer.position(info.offset);buffer.limit(info.offset+info.size);
                    muxer.writeSampleData(muxTrack,buffer,info);
                }
                boolean eos=(info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                codec.releaseOutputBuffer(index,false);
                if(eos) return true;
            }
        }
    }

    private void release(){
        try {
            if(display!=EGL14.EGL_NO_DISPLAY){
                if(program!=0) GLES20.glDeleteProgram(program);
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(eglSurface!=EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,eglSurface);
                if(context!=EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context);
                EGL14.eglTerminate(display);
            }
        } catch(Exception ignored){}
        try {if(codec!=null){codec.stop();codec.release();}}catch(Exception ignored){}
        try {if(inputSurface!=null)inputSurface.release();}catch(Exception ignored){}
        try {if(muxer!=null){if(muxStarted)muxer.stop();muxer.release();}}catch(Exception ignored){}
    }
}