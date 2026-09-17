"""Build the complete Tudur's Vehicle Mod sample addon pack.

Usage: python3 build_all.py <output_dir>
Produces:
  <output_dir>/sample_pack/                    - the loadable addon (drop into tudursvehiclemod-addons/)
  <output_dir>/reference_o_declaration/         - the SAME models, "o"-declared instead of "g" - see
                                                   models.py/lib.py's own doc for why both exist. Not a
                                                   loadable addon at all (no data/ or vehicles/ of its own).
"""
import os, sys, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import models, weapons, vehicles, assets

def main(out):
    root = os.path.join(out, "sample_pack")
    if os.path.exists(root): shutil.rmtree(root)
    ref = os.path.join(out, "reference_o_declaration")
    if os.path.exists(ref): shutil.rmtree(ref)
    A = os.path.join(root, "assets", "tudur_sample")
    models.build(os.path.join(A, "models", "obj"), os.path.join(A, "textures", "vehicle"),
                 out_obj_ref=os.path.join(ref, "models_obj_o_declared"))
    weapons.build(os.path.join(A, "weapons"))
    vehicles.build(os.path.join(root, "data", "tudur_sample", "vehicles"))
    assets.build(root)
    with open(os.path.join(ref, "README.md"), "w", encoding="utf-8") as f:
        f.write(
            "# reference_o_declaration\n\n"
            "sample_pack と全く同じ形状のモデルを、パーツのグループ宣言に\n"
            "`o`(object)を使って書き出した参考データです。sample_pack 本体は\n"
            "`g`(group)宣言を使っています(一部のソフトは`o`の繰り返し宣言を\n"
            "正しく扱えず、ファイル全体を単一オブジェクトとして読み込んでしまう\n"
            "ことがあるためです)。\n\n"
            "このフォルダには `data/` も `assets/<namespace>/vehicles/` もなく、\n"
            "単体では乗り物・武器を一切定義していないため、誤って\n"
            "`tudursvehiclemod-addons/` 配下に置いてもアドオンとして機能する\n"
            "ことはありません。あくまで両宣言方式の見た目・お使いのソフトでの\n"
            "読み込まれ方を見比べるための参考データです。\n"
        )
    print("sample pack written to", root)
    print("o-declared reference written to", ref)

if __name__ == "__main__":
    main(sys.argv[1])
