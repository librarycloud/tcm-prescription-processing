// Copyright (c) 2026 PaddlePaddle Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.paddle.ocr.util

import android.content.Context
import android.util.Log

object OpenCVUtils {

    @Volatile
    private var initialized = false

    var lastError: String? = null
        private set

    fun init(context: Context): Boolean {
        if (initialized) return true

        val errors = mutableListOf<String>()

        // 1. 尝试显式预加载 libc++_shared.so（ONNX Runtime 和 OpenCV 共同依赖）
        // 部分 Android 系统在 dlopen libopencv_java4 时若未预加载 c++_shared 会报 library not found
        try {
            System.loadLibrary("c++_shared")
            Log.d("OpenCVUtils", "c++_shared preloaded successfully")
        } catch (t: Throwable) {
            Log.d("OpenCVUtils", "c++_shared preload notice: ${t.message}")
        }

        // 2. 优先尝试 OpenCV 4.9+ 官方推荐的 OpenCVLoader.initLocal()
        try {
            if (org.opencv.android.OpenCVLoader.initLocal()) {
                initialized = true
                lastError = null
                Log.d("OpenCVUtils", "OpenCV initialized via OpenCVLoader.initLocal()")
                return true
            } else {
                errors.add("initLocal() returned false")
            }
        } catch (t: Throwable) {
            Log.d("OpenCVUtils", "OpenCVLoader.initLocal notice: ${t.message}")
        }

        // 3. 回退尝试旧版 OpenCVLoader.initDebug()
        try {
            @Suppress("DEPRECATION")
            val ok = org.opencv.android.OpenCVLoader.initDebug()
            if (ok) {
                initialized = true
                lastError = null
                Log.d("OpenCVUtils", "OpenCV initialized via OpenCVLoader.initDebug()")
                return true
            } else {
                errors.add("initDebug() returned false")
            }
        } catch (t: Throwable) {
            Log.w("OpenCVUtils", "OpenCVLoader.initDebug failed: ${t.message}")
            errors.add("initDebug: ${t.javaClass.simpleName}(${t.message})")
        }

        // 3. 回退尝试直接加载可能的 so 库名称
        val candidates = listOf("opencv_java4", "opencv_java", "opencv_java3")
        for (libName in candidates) {
            try {
                System.loadLibrary(libName)
                initialized = true
                lastError = null
                Log.d("OpenCVUtils", "OpenCV initialized via System.loadLibrary($libName)")
                return true
            } catch (t: Throwable) {
                Log.w("OpenCVUtils", "System.loadLibrary($libName) failed: ${t.message}")
                errors.add("loadLibrary($libName): ${t.javaClass.simpleName}(${t.message})")
            }
        }

        lastError = errors.joinToString("; ")
        Log.e("OpenCVUtils", "Failed to initialize OpenCV: $lastError")
        return false
    }
}
