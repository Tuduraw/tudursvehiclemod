# sample_pack_generator

`sample_pack/`(全乗り物タイプ・全武器タイプを網羅したサンプルアドオンパック)を
プログラム生成するスクリプト群です。

```
python3 build_all.py <出力先>        # <出力先>/sample_pack/ を生成
python3 validate.py <出力先>/sample_pack <プロジェクトルート>   # 参照整合性とドキュメント項目網羅の検査
python3 preview.py <出力先>/sample_pack/assets/tudur_sample sample_helicopter ...  # 簡易レンダリング
```

- `lib.py` … 箱組みOBJビルダー(スウォッチ式テクスチャアトラス)・スキン・合成サウンド
- `models.py` … 8機体+弾体モデル  - `weapons.py` … 武器設定24種  - `vehicles.py` … 機体JSON 8種
- `assets.py` … HUD・GUIテクスチャ・サウンド・ダミーパイロットスキン・lang・README

依存: Pillow, numpy, ffmpeg(サウンド生成時のみ)。

## パーツ宣言方式(o / g)について

`build_all.py`は2つの出力を生成します:

- `sample_pack/`(そのままアドオンとして使用可能): モデルは`g`(group)
  宣言でパーツ分けされています。一部の3DソフトはOBJの`o`(object)
  宣言の繰り返しを正しく扱えず、ファイル全体を単一オブジェクトとして
  読み込んでしまう場合があるため、より広く互換性のある`g`宣言を採用
  しています
- `reference_o_declaration/`(参考データ、アドオンとしては機能しない):
  全く同じ形状のモデルを`o`宣言で書き出したもの。`data/`・
  `assets/<namespace>/vehicles/`を持たないため、誤ってアドオン
  フォルダへ配置しても乗り物・武器としては一切機能しません

このMOD自身のOBJパーサー(`ObjModel.java`)は`o`・`g`のどちらも同一に
扱うため、`sample_pack`側の動作に影響はありません。
