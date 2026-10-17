# Third-party notices

Bundled components retain their upstream licenses. This file does not grant a new license to the first-party Plia code, project artwork or custom handwriting font.

| Component | Use | Notice |
| --- | --- | --- |
| KaTeX 0.19.0 | Local mathematical rendering and KaTeX font assets | MIT license at `app/src/main/assets/math/LICENSE`; origin and hashes in `VENDOR-MANIFEST.json` |
| Caveat | Original fallback handwriting font at `app/src/main/assets/handwriting.ttf` | SIL Open Font License at `app/src/main/assets/OFL.txt` |
| Gradle wrapper | Pinned build launcher and wrapper | Upstream notices at `gradle/LICENSE.txt` and wrapper-script headers |
| Gson 2.10.1 | JSON dependency resolved by Gradle | [Apache License 2.0 in the upstream project](https://github.com/google/gson/blob/gson-parent-2.10.1/LICENSE) |

`app/src/main/assets/math/fonts/AndreaHandwriting.ttf` is a custom project font, separate from KaTeX and Caveat. Its placement in the math asset directory does not make it a KaTeX font or place it under KaTeX's license. The source handwriting sheet is not included. No unrestricted reuse license is declared for that custom asset.

The original calculus lessons and bounded practice generators are first-party content. Referenced assignment PDFs, full source questions and answer keys are not distributed in this snapshot. External references are links and provenance metadata, not incorporated third-party course content.
