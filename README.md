# Muse YouTube Patches（ステータスバーをスクロールで隠す）

YouTube **21.30.216** 向けの単独 Morphe パッチ「Hide status bar on scroll」。
下部タブがスクロールで隠れるのに合わせて、上のステータスバー（背景ビュー含む）も隠す。実機確認済み（v0.1.2）。

- 単独バンドルのため設定画面はなく常時ON。切りたい時はパッチを外す
- 再生中と Shorts では隠さない（Morphe 拡張の PlayerType / ShortsPlayerState を実行時参照）
- ビルド: Gradle を使わない手組みパイプライン。`build.sh`（JDK・Morphe jar 等のパスは環境変数で指定）
- `variant/` … MorpheApp/morphe-patches 本体へ組み込む版の git パッチ（設定スイッチ付き）

