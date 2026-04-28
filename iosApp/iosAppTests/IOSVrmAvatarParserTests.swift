import Foundation
import Testing
@testable import VtuberCamera_KMP_ver

struct IOSVrmAvatarParserTests {
    @Test
    func parsesVrm0PreviewMetadataAndThumbnailFromBinaryChunk() throws {
        let thumbnailData = Data(base64Encoded: Self.onePixelPngBase64)!
        let glb = Self.createGlb(
            json: """
            {
              "asset": {"version": "2.0"},
              "extensions": {
                "VRM": {
                  "meta": {
                    "title": "Preview Avatar",
                    "author": "OpenAI",
                    "version": "0.99",
                    "texture": 0
                  }
                }
              },
              "textures": [{"source": 0}],
              "images": [{"bufferView": 0, "mimeType": "image/png"}],
              "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": \(thumbnailData.count)}]
            }
            """,
            binary: thumbnailData
        )

        let preview = try IOSVrmAvatarParser.parse(fileName: "preview-avatar.vrm", data: glb)

        #expect(preview.fileName == "preview-avatar.vrm")
        #expect(preview.avatarName == "Preview Avatar")
        #expect(preview.authorName == "OpenAI")
        #expect(preview.vrmVersion == "0.99")
        #expect(preview.thumbnail != nil)
    }

    @Test
    func parsesVrm1PreviewAndFallsBackToFileNameWhenMetadataNameIsMissing() throws {
        let glb = Self.createGlb(
            json: """
            {
              "asset": {"version": "2.0"},
              "extensions": {
                "VRMC_vrm": {
                  "specVersion": "1.0",
                  "meta": {
                    "authors": ["Author A", "Author B"],
                    "version": "1.2.3"
                  }
                }
              }
            }
            """,
            includeBinaryChunk: false
        )

        let preview = try IOSVrmAvatarParser.parse(fileName: "fallback-avatar.glb", data: glb)

        #expect(preview.fileName == "fallback-avatar.glb")
        #expect(preview.avatarName == "fallback-avatar")
        #expect(preview.authorName == "Author A")
        #expect(preview.vrmVersion == "1.2.3")
        #expect(preview.thumbnail == nil)
    }

    @Test
    func parsesPreviewFromFileUrl() throws {
        let directoryURL = try Self.makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directoryURL) }

        let fileURL = directoryURL.appendingPathComponent("disk-avatar.vrm")
        let glb = Self.createGlb(
            json: """
            {
              "asset": {"version": "2.0"},
              "extensions": {
                "VRM": {
                  "meta": {
                    "title": "Disk Avatar",
                    "author": "OpenAI"
                  }
                }
              }
            }
            """,
            includeBinaryChunk: false
        )
        try glb.write(to: fileURL)

        let preview = try IOSVrmAvatarParser.parse(url: fileURL)

        #expect(preview.fileName == "disk-avatar.vrm")
        #expect(preview.avatarName == "Disk Avatar")
        #expect(preview.authorName == "OpenAI")
        #expect(preview.thumbnail == nil)
    }

    @Test
    func rejectsUnsupportedFileExtensionForInMemoryParse() {
        do {
            _ = try IOSVrmAvatarParser.parse(fileName: "avatar.txt", data: Data([0x00]))
            Issue.record("Expected unsupported file extension to throw")
        } catch {
            #expect((error as NSError).localizedDescription == "VRM/GLBファイルを選択してください。")
        }
    }

    @Test
    func rejectsInvalidGlbChunkLayout() {
        let malformedGlb = Self.createGlb(
            json: """
            {
              "asset": {"version": "2.0"},
              "extensions": {
                "VRM": {
                  "meta": {
                    "title": "Broken Avatar"
                  }
                }
              }
            }
            """,
            declaredLengthOverride: 20
        )

        do {
            _ = try IOSVrmAvatarParser.parse(fileName: "broken.vrm", data: malformedGlb)
            Issue.record("Expected invalid chunk layout to throw")
        } catch {
            #expect((error as NSError).localizedDescription == "VRM/GLBファイルの形式が不正です。")
        }
    }

    @Test
    func rejectsMalformedJsonMetadata() {
        let malformedGlb = Self.createGlb(
            json: "{ this is not valid json !!!",
            includeBinaryChunk: false
        )

        do {
            _ = try IOSVrmAvatarParser.parse(fileName: "broken.vrm", data: malformedGlb)
            Issue.record("Expected malformed metadata to throw")
        } catch {
            #expect((error as NSError).localizedDescription == "VRM/GLBメタデータの解析に失敗しました。")
        }
    }

    @Test
    func rejectsNonFileUrls() {
        let remoteURL = URL(string: "https://example.com/avatar.vrm")!

        do {
            _ = try IOSVrmAvatarParser.parse(url: remoteURL)
            Issue.record("Expected non-file URL to throw")
        } catch {
            #expect((error as NSError).localizedDescription == "VRM/GLBファイルを選択してください。")
        }
    }

    @Test
    func rejectsFilesLargerThanMaximumAllowedWhenParsingFromUrl() throws {
        let directoryURL = try Self.makeTemporaryDirectory()
        defer { try? FileManager.default.removeItem(at: directoryURL) }

        let fileURL = directoryURL.appendingPathComponent("too-large.vrm")
        _ = FileManager.default.createFile(atPath: fileURL.path, contents: Data())
        let handle = try FileHandle(forWritingTo: fileURL)
        defer { handle.closeFile() }
        try handle.truncate(atOffset: UInt64(50 * 1024 * 1024 + 1))

        do {
            _ = try IOSVrmAvatarParser.parse(url: fileURL)
            Issue.record("Expected oversized file to throw")
        } catch {
            #expect((error as NSError).localizedDescription == "VRM/GLBファイルサイズが大きすぎます。")
        }
    }
}

private extension IOSVrmAvatarParserTests {
    static let onePixelPngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO5W7xkAAAAASUVORK5CYII="

    static func makeTemporaryDirectory() throws -> URL {
        let directoryURL = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directoryURL, withIntermediateDirectories: true)
        return directoryURL
    }

    static func createGlb(
        json: String,
        binary: Data = Data(),
        includeBinaryChunk: Bool = true,
        declaredLengthOverride: Int? = nil,
        magic: UInt32 = 0x46546C67
    ) -> Data {
        let jsonBytes = pad(Data(json.utf8), with: 0x20)
        let binaryBytes = pad(binary, with: 0x00)
        let totalLength = 12 + 8 + jsonBytes.count + (includeBinaryChunk ? 8 + binaryBytes.count : 0)

        var data = Data()
        data.appendLittleEndian(magic)
        data.appendLittleEndian(2)
        data.appendLittleEndian(declaredLengthOverride ?? totalLength)
        data.appendLittleEndian(jsonBytes.count)
        data.appendLittleEndian(0x4E4F534A)
        data.append(jsonBytes)

        if includeBinaryChunk {
            data.appendLittleEndian(binaryBytes.count)
            data.appendLittleEndian(0x004E4942)
            data.append(binaryBytes)
        }

        return data
    }

    static func pad(_ data: Data, with byte: UInt8) -> Data {
        let padding = (4 - data.count % 4) % 4
        guard padding > 0 else {
            return data
        }
        return data + Data(repeating: byte, count: padding)
    }
}

private extension Data {
    mutating func appendLittleEndian(_ value: Int) {
        appendLittleEndian(UInt32(value))
    }

    mutating func appendLittleEndian(_ value: UInt32) {
        var littleEndian = value.littleEndian
        Swift.withUnsafeBytes(of: &littleEndian) { bytes in
            append(contentsOf: bytes)
        }
    }
}
