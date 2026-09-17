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

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDouble
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

object BitmapUtils {

    fun imdecodeBGR(imageBytes: ByteArray): Mat {
        val encoded = MatOfByte(*imageBytes)
        return try {
            Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR)
        } finally {
            encoded.release()
        }
    }

    fun bitmapToBGRMat(bitmap: Bitmap): Mat {
        return bitmapToMat(bitmap, Imgproc.COLOR_RGBA2BGR)
    }

    fun bitmapToRGBMat(bitmap: Bitmap): Mat {
        return bitmapToMat(bitmap, Imgproc.COLOR_RGBA2RGB)
    }

    fun bgrMatToBitmap(mat: Mat): Bitmap {
        val rgba = Mat()
        return try {
            Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
            Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888).also { bitmap ->
                Utils.matToBitmap(rgba, bitmap)
            }
        } finally {
            rgba.release()
        }
    }

    /**
     * 计算图像清晰度（拉普拉斯方差）。
     * 在缩放后的微缩图上运行，耗时 < 0.5ms。
     * 用于在相机大幅晃动/运动拖影时过滤无效模糊帧，避免重影导致的字符误判与变长。
     */
    fun calculateSharpness(bitmap: Bitmap): Double {
        val targetW = 160
        val targetH = (bitmap.height.toFloat() / bitmap.width * targetW).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, false)
        val rgba = Mat(scaled.height, scaled.width, CvType.CV_8UC4)
        val gray = Mat()
        val laplacian = Mat()
        val mean = MatOfDouble()
        val stddev = MatOfDouble()
        return try {
            Utils.bitmapToMat(scaled, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, mean, stddev)
            val std = stddev.get(0, 0)[0]
            std * std
        } catch (e: Throwable) {
            100.0 // 异常时兜底放行
        } finally {
            if (scaled != bitmap) {
                scaled.recycle()
            }
            rgba.release()
            gray.release()
            laplacian.release()
            mean.release()
            stddev.release()
        }
    }

    private fun bitmapToMat(bitmap: Bitmap, colorConversionCode: Int): Mat {
        val bmp = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat(bmp.height, bmp.width, CvType.CV_8UC4)
        val dst = Mat()
        return try {
            Utils.bitmapToMat(bmp, rgba)
            Imgproc.cvtColor(rgba, dst, colorConversionCode)
            dst
        } catch (t: Throwable) {
            dst.release()
            throw t
        } finally {
            bmp.recycle()
            rgba.release()
        }
    }
}
