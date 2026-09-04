# Quanda Bible Amplifiée — AndBible-based edition

This directory contains a derivative work based on **AndBible: Bible Study**.

Upstream: https://github.com/AndBible/and-bible
Imported branch: `current-stable`
Upstream license: GNU General Public License, version 3 or later.

The upstream source code and graphical resources are retained under their applicable copyright and GPL notices. Modifications made for Quanda include visible application branding, the Android application ID, and bundling of the user-provided `EnglishAmplifiedBible.xml` for the Quanda integration layer.

Distribution of a binary derived from this source must comply with GPL-3.0-or-later, including making the corresponding source code and license notices available. Bible text licensing is separate from the AndBible software license and must be verified independently before public redistribution.

## Bundled Bible module

`EnglishAmplifiedBible.xml` is converted at build time to OSIS and then to a compressed SWORD `zText` module named `QuandaAMP`. `StartupActivity` installs that bundled module headlessly on first launch through AndBible's existing `BackupControl.extractAndRegisterModuleArchive` implementation, so the Amplified text is available inside the native AndBible study engine.

## Quanda changes

- Visible product name: **Quanda Bible Amplifiée**.
- Standard Android application ID: `com.webmotion.quandabible.amplifiee`.
- The native AndBible source tree, JSword study engine and upstream graphical resources are intentionally retained instead of reimplementing them.
- The application is therefore a GPL derivative of AndBible, not merely a look-alike implementation.
