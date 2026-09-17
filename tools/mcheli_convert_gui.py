#!/usr/bin/env python3
"""
mcheli_convert_gui.py - GUIでmcheli_convert.pyを実行するためのシンプルなランチャー。

コマンドライン・Pythonの知識が無い人向けに、フォルダ選択ダイアログと進捗ログだけの
簡単なウィンドウを提供します。変換ロジック自体はmcheli_convert.pyをそのまま呼び出す
ため、コマンドライン版と全く同じ変換結果になります。

起動方法:
    python3 mcheli_convert_gui.py
(ダブルクリックで起動できるよう、環境によっては拡張子を.pywにリネームするか、
ショートカットを作成してください)

追加のライブラリのインストールは不要です(tkinterはPython標準ライブラリに含まれます)。
"""
import queue
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

import mcheli_convert


class ConverterGui:
    def __init__(self, root):
        self.root = root
        root.title("MCヘリ変換ツール")
        root.geometry("640x480")
        root.minsize(560, 400)

        self.addon_path = tk.StringVar()
        self.output_path = tk.StringVar()
        self.namespace = tk.StringVar(value="mcheliport")
        self.disable_auto_detect = tk.BooleanVar(value=False)
        self.message_queue = queue.Queue()
        self.running = False

        self._build_widgets()
        self.root.after(100, self._poll_queue)

    def _build_widgets(self):
        pad = {"padx": 8, "pady": 6}

        frame_in = ttk.LabelFrame(self.root, text="変換元(MCヘリアドオンパックのフォルダ)")
        frame_in.pack(fill="x", **pad)
        ttk.Entry(frame_in, textvariable=self.addon_path).pack(side="left", fill="x", expand=True, padx=(8, 4), pady=8)
        ttk.Button(frame_in, text="参照...", command=self._pick_addon_folder).pack(side="left", padx=(0, 8), pady=8)

        frame_out = ttk.LabelFrame(self.root, text="変換先(出力フォルダ) - 未入力時は変換元と同じ場所に自動作成")
        frame_out.pack(fill="x", **pad)
        ttk.Entry(frame_out, textvariable=self.output_path).pack(side="left", fill="x", expand=True, padx=(8, 4), pady=8)
        ttk.Button(frame_out, text="参照...", command=self._pick_output_folder).pack(side="left", padx=(0, 8), pady=8)

        frame_opts = ttk.LabelFrame(self.root, text="オプション")
        frame_opts.pack(fill="x", **pad)
        ns_row = ttk.Frame(frame_opts)
        ns_row.pack(fill="x", padx=8, pady=(8, 4))
        ttk.Label(ns_row, text="名前空間:").pack(side="left")
        ttk.Entry(ns_row, textvariable=self.namespace, width=20).pack(side="left", padx=(4, 0))
        ttk.Checkbutton(
            frame_opts,
            text="乗り物の種類を自動判定しない(常にフォルダ名通りに変換する)",
            variable=self.disable_auto_detect,
        ).pack(anchor="w", padx=8, pady=(4, 8))

        self.convert_button = ttk.Button(self.root, text="変換開始", command=self._start_conversion)
        self.convert_button.pack(pady=(0, 8))

        frame_log = ttk.LabelFrame(self.root, text="進捗")
        frame_log.pack(fill="both", expand=True, **pad)
        self.log_text = tk.Text(frame_log, wrap="word", state="disabled", height=12)
        scrollbar = ttk.Scrollbar(frame_log, command=self.log_text.yview)
        self.log_text.configure(yscrollcommand=scrollbar.set)
        self.log_text.pack(side="left", fill="both", expand=True, padx=(8, 0), pady=8)
        scrollbar.pack(side="right", fill="y", padx=(0, 8), pady=8)

    def _pick_addon_folder(self):
        path = filedialog.askdirectory(title="MCヘリアドオンパックのフォルダを選択")
        if path:
            self.addon_path.set(path)

    def _pick_output_folder(self):
        path = filedialog.askdirectory(title="出力先フォルダを選択")
        if path:
            self.output_path.set(path)

    def _log(self, message: str):
        self.log_text.configure(state="normal")
        self.log_text.insert("end", message + "\n")
        self.log_text.see("end")
        self.log_text.configure(state="disabled")

    def _start_conversion(self):
        if self.running:
            return
        addon_root = self.addon_path.get().strip()
        out_root = self.output_path.get().strip()
        if not addon_root:
            messagebox.showerror("エラー", "変換元のフォルダを指定してください。")
            return
        if not out_root:
            # <変換元の親フォルダ>/<変換元フォルダ名>_convert - leaving the
            # output field blank still gives a
            # predictable, discoverable destination rather than an error.
            addon_path_obj = Path(addon_root)
            out_root = str(addon_path_obj.parent / f"{addon_path_obj.name}_convert")
            self.output_path.set(out_root)

        self.running = True
        self.convert_button.configure(state="disabled")
        self.log_text.configure(state="normal")
        self.log_text.delete("1.0", "end")
        self.log_text.configure(state="disabled")

        thread = threading.Thread(
            target=self._run_conversion_thread,
            args=(addon_root, out_root, self.namespace.get().strip() or "mcheliport", self.disable_auto_detect.get()),
            daemon=True,
        )
        thread.start()

    def _run_conversion_thread(self, addon_root, out_root, namespace, disable_auto_detect):
        try:
            converted, skipped, _ = mcheli_convert.run_conversion(
                Path(addon_root), Path(out_root), namespace, disable_auto_detect,
                progress_callback=lambda msg: self.message_queue.put(("log", msg)),
            )
            self.message_queue.put(("done", (converted, skipped)))
        except Exception as e:
            self.message_queue.put(("error", str(e)))

    def _poll_queue(self):
        try:
            while True:
                kind, payload = self.message_queue.get_nowait()
                if kind == "log":
                    self._log(payload)
                elif kind == "done":
                    converted, skipped = payload
                    self._log(f"\n完了しました。変換: {converted}件、スキップ: {skipped}件")
                    self._log("詳細は出力フォルダ内のconvert_report.txtを確認してください。")
                    self.running = False
                    self.convert_button.configure(state="normal")
                    messagebox.showinfo("完了", f"変換が完了しました。\n変換: {converted}件 / スキップ: {skipped}件")
                elif kind == "error":
                    self._log(f"\nエラー: {payload}")
                    self.running = False
                    self.convert_button.configure(state="normal")
                    messagebox.showerror("エラー", payload)
        except queue.Empty:
            pass
        self.root.after(100, self._poll_queue)


def main():
    root = tk.Tk()
    ConverterGui(root)
    root.mainloop()


if __name__ == "__main__":
    main()
