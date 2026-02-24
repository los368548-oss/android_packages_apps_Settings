/*
 * Copyright (C) 2025 The HavocOOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.deviceinfo.aboutphone;

import android.content.Context;
import android.opengl.GLES20;
import android.os.SystemProperties;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.LayoutPreference;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Controller for displaying GPU information.
 * Shows GPU renderer and vendor information.
 */
public class GpuInfoPreferenceController extends BasePreferenceController {

    private static final String KEY_GPU_INFO = "gpu_info";

    private LayoutPreference mPreference;

    public GpuInfoPreferenceController(Context context) {
        super(context, KEY_GPU_INFO);
    }

    public GpuInfoPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(KEY_GPU_INFO);
        if (mPreference != null) {
            setupCard();
        }
    }

    private void setupCard() {
        // Set icon
        ImageView iconView = mPreference.findViewById(R.id.device_info_icon);
        if (iconView != null) {
            iconView.setImageResource(R.drawable.ic_gpu_info);
        }

        // Set title
        TextView titleView = mPreference.findViewById(R.id.device_info_title);
        if (titleView != null) {
            titleView.setText(R.string.gpu_info_title);
        }

        // Set summary with GPU info
        TextView summaryView = mPreference.findViewById(R.id.device_info_summary);
        if (summaryView != null) {
            summaryView.setText(getGpuSummary());
        }

        // Hide progress bar
        View progressContainer = mPreference.findViewById(R.id.progress_container);
        if (progressContainer != null) {
            progressContainer.setVisibility(View.GONE);
        }
    }

    private String getGpuSummary() {
        // Try to get GPU info from system property first
        String gpuProp = SystemProperties.get("ro.havoc.gpu", "");
        if (!gpuProp.isEmpty()) {
            return gpuProp;
        }

        // Fallback to OpenGL ES info
        String[] gpuInfo = getGpuInfoFromGL();
        if (gpuInfo != null && gpuInfo.length >= 2) {
            String renderer = gpuInfo[0];
            String vendor = gpuInfo[1];
            
            // Simplify renderer name if too long
            if (renderer.length() > 40) {
                renderer = renderer.substring(0, 37) + "...";
            }
            
            return renderer;
        }
        
        return mContext.getString(R.string.gpu_info_default);
    }

    private String[] getGpuInfoFromGL() {
        try {
            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            
            int[] version = new int[2];
            if (!egl.eglInitialize(display, version)) {
                return null;
            }
            
            int[] configAttribs = {
                EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
            };
            
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl.eglChooseConfig(display, configAttribs, configs, 1, numConfigs)) {
                return null;
            }
            
            int[] contextAttribs = {
                0x3098, // EGL_CONTEXT_CLIENT_VERSION
                2,
                EGL10.EGL_NONE
            };
            
            EGLContext context = egl.eglCreateContext(display, configs[0], 
                    EGL10.EGL_NO_CONTEXT, contextAttribs);
            
            int[] surfaceAttribs = {
                EGL10.EGL_WIDTH, 1,
                EGL10.EGL_HEIGHT, 1,
                EGL10.EGL_NONE
            };
            
            EGLSurface surface = egl.eglCreatePbufferSurface(display, 
                    configs[0], surfaceAttribs);
            
            egl.eglMakeCurrent(display, surface, surface, context);
            
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
            
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, 
                    EGL10.EGL_NO_CONTEXT);
            egl.eglDestroySurface(display, surface);
            egl.eglDestroyContext(display, context);
            egl.eglTerminate(display);
            
            return new String[] { renderer, vendor };
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isSliceable() {
        return false;
    }
}
