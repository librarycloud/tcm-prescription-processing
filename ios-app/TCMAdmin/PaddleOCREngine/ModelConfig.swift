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

import Foundation

/// Resolves exported ONNX or ORT-format models and their **model config files** in the app bundle (`Models/det`, `Models/rec`).
/// Resource lookup uses the conventional shared basename for the weight file and the model config file in each folder.
/// Weights: prefers `inference*.ort` in the model directory (see ``bundledOrtPath``), otherwise `inference.onnx`.
struct ModelConfig {
    let modelPath: String
    let configPath: String
    /// From `Global.model_name` in the model config file (same directory as the ONNX weights).
    let name: String

    /// Human-readable label from `Global.model_name` in the model config file; `fallback` if the file cannot be read.
    private static func displayName(fromModelConfigPath configPath: String, fallback: String) -> String {
        guard let cfg = try? InferenceConfig.load(from: configPath) else {
            return fallback
        }
        return cfg.modelName
    }

    /// Bundled model weights: any `inference*.ort` in the subdirectory (``ortBundledFileMatchOrder``), else `inference.onnx`.
    ///
    /// ONNX Runtime’s `convert_onnx_models_to_ort` may emit `inference.with_runtime_opt.ort` instead of `inference.ort`
    /// when using Runtime optimization style; the bundle cannot resolve that with `forResource: "inference"`.
    private static let ortResourceStems: [String] = [
        "inference",
        "inference.with_runtime_opt",
    ]

    private static func bundledOrtPath(prefix: String) -> String? {
        // Just directly try the prefix (e.g. det_inference.ort)
        if let p = Bundle.main.path(forResource: prefix, ofType: "ort") {
            return p
        }
        return nil
    }

    private static func bundledWeightsPath(prefix: String, notFoundMessage: String) throws -> String {
        if let ort = bundledOrtPath(prefix: prefix) {
            return ort
        }
        if let onnx = Bundle.main.path(forResource: prefix, ofType: "onnx") {
            return onnx
        }
        throw ModelConfigError.modelNotFound(notFoundMessage)
    }

    nonisolated static func detection() throws -> ModelConfig {
        let modelPath = try Self.bundledWeightsPath(
            prefix: "det_inference",
            notFoundMessage: "det_inference.ort or det_inference.onnx"
        )
        guard let configPath = Bundle.main.path(forResource: "det_inference", ofType: "yml") else {
            throw ModelConfigError.modelNotFound("det model config file")
        }
        let name = displayName(fromModelConfigPath: configPath, fallback: "text_detection")
        return ModelConfig(modelPath: modelPath, configPath: configPath, name: name)
    }

    nonisolated static func recognition() throws -> ModelConfig {
        let modelPath = try Self.bundledWeightsPath(
            prefix: "rec_inference",
            notFoundMessage: "rec_inference.ort or rec_inference.onnx"
        )
        guard let configPath = Bundle.main.path(forResource: "rec_inference", ofType: "yml") else {
            throw ModelConfigError.modelNotFound("rec model config file")
        }
        let name = displayName(fromModelConfigPath: configPath, fallback: "text_recognition")
        return ModelConfig(modelPath: modelPath, configPath: configPath, name: name)
    }
}

enum ModelConfigError: LocalizedError {
    case modelNotFound(String)

    var errorDescription: String? {
        switch self {
        case .modelNotFound(let path):
            return "Model file not found in bundle: \(path)"
        }
    }
}
