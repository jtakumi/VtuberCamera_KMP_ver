import Foundation
import UIKit

struct IOSAvatarPreview {
    let fileName: String
    let avatarName: String
    let authorName: String?
    let vrmVersion: String?
    let thumbnail: UIImage?
}

enum IOSVrmAvatarParser {
    private static let supportedExtensions: Set<String> = ["vrm", "glb"]

    static func parse(url: URL) throws -> IOSAvatarPreview {
        let didAccess = url.startAccessingSecurityScopedResource()
        defer {
            if didAccess {
                url.stopAccessingSecurityScopedResource()
            }
        }

        let fileName = url.lastPathComponent
        guard supportedExtensions.contains(url.pathExtension.lowercased()) else {
            throw ParserError.invalidFileType
        }

        let data = try Data(contentsOf: url)
        let fileBytes = [UInt8](data)
        guard fileBytes.count >= 20 else {
            throw ParserError.readFailed
        }
        guard readIntLE(fileBytes, offset: 0) == 0x46546C67 else {
            throw ParserError.invalidFileType
        }

        let declaredLength = readIntLE(fileBytes, offset: 8)
        guard declaredLength <= fileBytes.count, declaredLength >= 20 else {
            throw ParserError.invalidFormat
        }

        var offset = 12
        var jsonChunk: Data?
        var binaryChunk: Data?
        while offset + 8 <= declaredLength {
            let chunkLength = readIntLE(fileBytes, offset: offset)
            let chunkType = readIntLE(fileBytes, offset: offset + 4)
            let chunkStart = offset + 8
            let chunkEnd = chunkStart + chunkLength
            guard chunkLength >= 0, chunkEnd <= declaredLength else {
                throw ParserError.invalidFormat
            }

            let chunkData = data.subdata(in: chunkStart..<chunkEnd)
            if chunkType == 0x4E4F534A {
                jsonChunk = chunkData
            } else if chunkType == 0x004E4942 {
                binaryChunk = chunkData
            }
            offset = chunkEnd
        }

        guard
            let jsonChunk,
            let jsonObject = try JSONSerialization.jsonObject(with: jsonChunk) as? [String: Any]
        else {
            throw ParserError.metadataFailed
        }

        let meta0 = (((jsonObject["extensions"] as? [String: Any])?["VRM"] as? [String: Any])?["meta"] as? [String: Any])
        let meta1 = (((jsonObject["extensions"] as? [String: Any])?["VRMC_vrm"] as? [String: Any])?["meta"] as? [String: Any])
        let avatarName = (meta0?["title"] as? String)
            ?? (meta1?["name"] as? String)
            ?? (fileName as NSString).deletingPathExtension
        let authorName = (meta0?["author"] as? String)
            ?? ((meta1?["authors"] as? [String])?.first)
        let vrmVersion = (meta0?["version"] as? String)
            ?? (((jsonObject["extensions"] as? [String: Any])?["VRMC_vrm"] as? [String: Any])?["specVersion"] as? String)
            ?? ((jsonObject["asset"] as? [String: Any])?["version"] as? String)

        let thumbnailImageIndex: Int? = {
            if
                let textureIndex = meta0?["texture"] as? Int,
                let textures = jsonObject["textures"] as? [[String: Any]],
                textureIndex >= 0,
                textureIndex < textures.count,
                let source = textures[textureIndex]["source"] as? Int
            {
                return source
            }
            return meta1?["thumbnailImage"] as? Int
        }()

        let thumbnail: UIImage? = {
            guard
                let thumbnailImageIndex,
                let binaryChunk,
                let images = jsonObject["images"] as? [[String: Any]],
                thumbnailImageIndex >= 0,
                thumbnailImageIndex < images.count,
                let bufferViewIndex = images[thumbnailImageIndex]["bufferView"] as? Int,
                let mimeType = images[thumbnailImageIndex]["mimeType"] as? String,
                mimeType.hasPrefix("image/"),
                let bufferViews = jsonObject["bufferViews"] as? [[String: Any]],
                bufferViewIndex >= 0,
                bufferViewIndex < bufferViews.count,
                let byteLength = bufferViews[bufferViewIndex]["byteLength"] as? Int
            else {
                return nil
            }

            let byteOffset = (bufferViews[bufferViewIndex]["byteOffset"] as? Int) ?? 0
            let end = byteOffset + byteLength
            guard byteOffset >= 0, end <= binaryChunk.count else {
                return nil
            }
            let imageData = binaryChunk.subdata(in: byteOffset..<end)
            return UIImage(data: imageData)
        }()

        return IOSAvatarPreview(
            fileName: fileName,
            avatarName: avatarName,
            authorName: authorName,
            vrmVersion: vrmVersion,
            thumbnail: thumbnail
        )
    }

    private static func readIntLE(_ bytes: [UInt8], offset: Int) -> Int {
        Int(bytes[offset])
            | (Int(bytes[offset + 1]) << 8)
            | (Int(bytes[offset + 2]) << 16)
            | (Int(bytes[offset + 3]) << 24)
    }

    private enum ParserError: LocalizedError {
        case invalidFileType
        case readFailed
        case invalidFormat
        case metadataFailed

        var errorDescription: String? {
            switch self {
            case .invalidFileType:
                return "VRM/GLBファイルを選択してください。"
            case .readFailed:
                return "VRM/GLBファイルの読み込みに失敗しました。"
            case .invalidFormat:
                return "VRM/GLBファイルの形式が不正です。"
            case .metadataFailed:
                return "VRM/GLBメタデータの解析に失敗しました。"
            }
        }
    }
}