# iOS 録音音量調整の録画設計

## 目的

iOS の動画録画で、設定済みの `microphoneGain`（0〜2、100% が 1.0）を録音音声へ反映する。端末ごとに対応状況が異なる `AVAudioSession.inputGain` に依存せず、独立したマイクトラックへソフトウェア gain を適用する。

## 現状

`IOSScreenVideoRecorder` は ReplayKit で画面とアプリ音声を録画し、`AVAudioEngine` の input tap で gain を適用したマイク音声を別録りする。停止後に AVFoundation でトラックを合成する。

## 採用する構成

1. `RPScreenRecorder` は画面とアプリ音声を録画し、`microphoneEnabled = false` にする。`AVAudioEngine` の input tap で PCM を受け、gain を適用した後に `AVAudioFile` で AAC / m4a の一時ファイルへ別録りする。
2. `AVAudioSession` の録音権限を確認し、未決定なら録音開始前に要求する。拒否時はマイク録音を省き、既存仕様どおり画面とアプリ音声の録画を続ける。
3. 停止後、ReplayKit の動画とマイク音声を `AVMutableComposition` へ別トラックとして挿入する。ReplayKit の映像とアプリ音声はそのまま保持する。
4. `AVMutableComposition` のマイクトラックだけへ音量係数 1.0 を設定し、`AVAssetExportSession` で音声トラックをミックスして MP4 へ書き出す。gain は書き出し前の PCM 段階で適用する。`0` は無音、`1` は原音、`2` は最大 200% とし、BGM を含むアプリ音声には gain を適用しない。
5. 完成した一時 MP4 を Photos に保存し、成否に関わらず画面録画・マイク録音・合成ファイルを片付ける。

録画開始時に `VideoRecordingAudioOptions` を固定し、その録画中は同じ gain を使う。録画中の設定変更は現行 UI と同じく受け付けない。マイク権限がない場合は映像と取得可能なアプリ音声だけを保存し、マイク用の音声トラックは追加しない。

## UI・共有契約

- 共有の `microphoneGain`、設定保存、録画開始時の値渡しを維持する。Android と同じ 0〜2 の意味を iOS にも適用する。
- iOS の `supportsMicrophoneGain` はマイクトラックへの音量適用と MP4 export の実装後に `true` とし、スライダーを表示する。
- gain はマイク音声だけに掛ける。アプリ再生音（BGM）は元の音量を維持する。
- gain 適用後の飽和は clamp で防ぐ。2.0 を超える値は共有設定境界で既存仕様どおり上限へ丸める。

## ライフサイクルと失敗時の扱い

- ReplayKit または AVAudioEngine の開始失敗、PCM 書き込み失敗、トラック合成失敗、export 失敗を録画失敗として通知し、一時ファイルを解放する。
- マイクの許可が拒否されたときは既存仕様どおり映像のみ（取得できるアプリ音声は含む）で録画する。
- マイク録音ファイルが利用できない場合は ReplayKit の映像とアプリ音声をそのまま保存する。合成・export 自体が失敗した場合は録画失敗として通知する。
- AVAudioEngine と ReplayKit は別々に開始するため、開始時に短い同期ずれが起きうる。実機で測定し、許容できない場合は録音開始時刻を補正する方式へ変更する。
- 雑音抑制の `AVAudioSession` 設定は録画開始前に行い、全ての終了経路で元のモードへ戻す。

## 実装順

実装済み: ReplayKit と AVAudioEngine の開始・停止、権限拒否、別トラック合成、マイク音量を適用した MP4 export、iOS の設定スライダー表示。

残る確認: 実機で gain の境界値（0 / 1 / 2）、BGM とマイクの分離、同期、Bluetooth / 有線入力、権限拒否、長時間録画、停止連打、Photos 保存を確認する。

## 制約・確認事項

- `AVAssetExportSession` で音声トラックをミックスするため、映像も再エンコードされる。出力品質と export 所要時間を実機で確認する。
- この設計は録画時にマイク別ファイルと合成後 MP4 の一時容量を必要とする。空き容量不足と export 失敗をユーザーへ録画失敗として返す。
- この文書の設計は実装済み。実機での同期・音質・権限フロー確認は未完了。
