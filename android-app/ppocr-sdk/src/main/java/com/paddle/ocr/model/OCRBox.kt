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

package com.paddle.ocr.model

import android.graphics.PointF

data class OCRBox(
    val points: List<PointF>,
) {
    init {
        require(points.size == 4) { "OCRBox must have exactly 4 points, got ${points.size}" }
    }

    val width: Float
        get() {
            val w1 = kotlin.math.hypot(points[1].x - points[0].x, points[1].y - points[0].y)
            val w2 = kotlin.math.hypot(points[2].x - points[3].x, points[2].y - points[3].y)
            return maxOf(w1, w2)
        }

    val height: Float
        get() {
            val h1 = kotlin.math.hypot(points[3].x - points[0].x, points[3].y - points[0].y)
            val h2 = kotlin.math.hypot(points[2].x - points[1].x, points[2].y - points[1].y)
            return maxOf(h1, h2)
        }

    val centerX: Float
        get() = (points[0].x + points[1].x + points[2].x + points[3].x) / 4f

    val centerY: Float
        get() = (points[0].y + points[1].y + points[2].y + points[3].y) / 4f

    val aspectRatio: Float
        get() = width / height.coerceAtLeast(1e-4f)
}
