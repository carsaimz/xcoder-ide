#!/usr/bin/env python3
"""Generate Android locale resources using the public Google Translate endpoint.

This is intentionally best-effort: failed translations fall back to English so the
application always has a complete, valid resource table.
"""
from __future__ import annotations

import html
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Dict

import requests

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/res/values/strings.xml"
OUT_ROOT = ROOT / "app/src/main/res"
TARGETS = {
    "values": ("pt-PT", "Português (Portugal)"),
    "values-en": ("en", "English"),
    "values-es": ("es", "Español"),
    "values-fr": ("fr", "Français"),
    "values-de": ("de", "Deutsch"),
    "values-it": ("it", "Italiano"),
    "values-ru": ("ru", "Русский"),
    "values-zh-rCN": ("zh-CN", "简体中文"),
    "values-ja": ("ja", "日本語"),
    "values-ko": ("ko", "한국어"),
    "values-ar": ("ar", "العربية"),
    "values-pt-rBR": ("pt-BR", "Português (Brasil)"),
}
STRING_RE = re.compile(r'<string name="([^"]+)">(.*?)</string>')
PLACEHOLDER_RE = re.compile(r"%\d+\$[sdfox]|%[sdfox]")


def parse_source() -> Dict[str, str]:
    return {key: html.unescape(value) for key, value in STRING_RE.findall(SOURCE.read_text(encoding="utf-8"))}


def translate_one(value: str, target: str) -> str:
    if target == "en" or not value.strip():
        return value
    placeholders = PLACEHOLDER_RE.findall(value)
    protected = value
    for index, placeholder in enumerate(placeholders):
        protected = protected.replace(placeholder, f"XCODERPLACEHOLDER{index}X", 1)
    for attempt in range(3):
        try:
            response = requests.get(
                "https://api.mymemory.translated.net/get",
                params={"q": protected, "langpair": f"en|{target}"},
                timeout=30,
            )
            response.raise_for_status()
            data = response.json()
            translated = str(data.get("responseData", {}).get("translatedText", ""))
            for index, placeholder in enumerate(placeholders):
                translated = translated.replace(f"XCODERPLACEHOLDER{index}X", placeholder)
            if translated.upper().startswith("MYMEMORY WARNING") or translated.upper().startswith("PLEASE"):
                return value
            if placeholders and PLACEHOLDER_RE.findall(translated) != placeholders:
                return value
            return translated or value
        except Exception:
            if attempt == 2:
                return value
            time.sleep(0.6 * (attempt + 1))
    return value


def xml_escape(value: str) -> str:
    return html.escape(value, quote=False)


def write_resource(directory: str, values: Dict[str, str]) -> None:
    out_dir = OUT_ROOT / directory
    out_dir.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    lines += [f'    <string name="{key}">{xml_escape(value)}</string>' for key, value in values.items()]
    lines.append('</resources>')
    (out_dir / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    source = parse_source()
    items = list(source.items())
    write_resource("values-en", source)
    for directory, (target, label) in TARGETS.items():
        if directory == "values-en":
            continue
        translated: Dict[str, str] = {}
        with ThreadPoolExecutor(max_workers=10) as executor:
            futures = {executor.submit(translate_one, value, target): key for key, value in items}
            for index, future in enumerate(as_completed(futures), 1):
                translated[futures[future]] = future.result()
                if index % 50 == 0 or index == len(items):
                    print(f"{label}: {index}/{len(items)}", flush=True)
        write_resource(directory, {key: translated.get(key, value) for key, value in items})


if __name__ == "__main__":
    main()
