# Store Review Checklist

## 1. Permission UX

- 権限が必要な理由が画面上で分かるか
- permission 拒否時に行き止まりにならないか
- 設定アプリ遷移が自然か
- permission 未許可時に誤解を招く表示をしていないか

## 2. Broken Or Misleading UI

- 押せる見た目の未実装ボタンがないか
- 未対応機能を動くように見せていないか
- ローディングが解除されない状態がないか
- エラー時に復帰方法が分かるか

## 3. Privacy And Data Expectations

- カメラ、顔追跡、ファイル選択の目的が自然言語で伝わるか
- ローカル処理かアップロードか誤解を招く文言がないか
- 個人情報を扱う印象を与えるのに説明がない状態でないか

## 4. Device Compatibility And Fallback

- 非対応端末で壊れた UI を見せていないか
- fallback 未実装なら、そのことがユーザーに分かるか
- カメラ非対応、AR 非対応、tracking 不可時の画面があるか

## 5. Purchase / Unlock / External Navigation

- 課金や解放条件がある場合、誤認を招く文言がないか
- 外部サイトや設定画面遷移が突然発生しないか
- ストア審査担当が再現不能な画面に閉じ込められないか

## 6. Content And Trust

- 過度に誤解を招く表現や fake system UI がないか
- 実際にはない解析能力や保証を示唆していないか
- 失敗時や制限時に user trust を損なう唐突な挙動がないか

## 7. Recommended Output Heuristic

- P0: 審査落ちの可能性が高い、または画面が壊れている
- P1: 説明不足や導線不備で差し戻しリスクがある
- P2: 改善推奨だが blocker ではない