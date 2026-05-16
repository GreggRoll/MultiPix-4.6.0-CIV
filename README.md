# MultiPix

MultiPix is an ATAK plugin that provides an in-plugin multi-photo capture workflow. It opens a plugin-owned camera experience, lets the user capture multiple images in one session, and then returns to ATAK for review before saving the kept images to a single marker.

[Download APK](https://github.com/GreggRoll/MultiPix-4.6.0-CIV/releases/download/v1.1/ATAK-Plugin-MultiPix-1.1--5.5.0-civ-release.apk)
## Purpose and Capabilities

MultiPix is designed for fast field collection when several photos need to be tied to the same location.

- Launches from the ATAK sidebar
- Opens a full-screen plugin camera workflow
- Captures multiple photos in one session
- Returns to ATAK with a review panel for the captured session
- Lets the user add captions and delete unwanted images
- Saves all kept images to one ATAK marker
- Geotags every saved image to the same shared point

## Status

Current status: in active development

This repository currently includes a CIV debug APK build for testing:

- ATAK target: `4.6.0.CIV`
- Build type: `civDebug`
- Output: `app/build/outputs/apk/civ/debug/ATAK-Plugin-Test Plugin-1.0--4.6.0-civ-debug.apk`

## Point of Contact

Development point of contact: project maintainer / repository owner

Update this section with the preferred name, team, and contact method before wider distribution.

## Ports Required

None known.

MultiPix is a local ATAK plugin workflow and does not currently require any dedicated network ports for operation.

## Equipment Required

- Android device running ATAK CIV `4.6.0`
- Device camera
- Storage access for saving captured images and plugin attachments

## Equipment Supported

- Android tablets and phones supported by ATAK CIV `4.6.0`
- Devices with a functional rear camera

The current workflow is being tuned for landscape tablet use inside ATAK.

## User Workflow

1. Select MultiPix from the ATAK sidebar.
2. Capture as many photos as needed in the plugin camera view.
3. Tap `Done` to return to ATAK.
4. Review the session in the right-side panel.
5. Add captions or delete unwanted photos.
6. Save the session to create one marker with all kept images attached.

## Compilation

Build the CIV debug APK from the plugin root:

```powershell
.\gradlew.bat assembleCivDebug
```

Expected output:

```text
app/build/outputs/apk/civ/debug/ATAK-Plugin-Test Plugin-1.0--4.6.0-civ-debug.apk
```

## Developer Notes

- The plugin currently targets the ATAK CIV flavor.
- The README APK link points to the built debug artifact currently stored in the repository tree.
- Camera orientation behavior is currently being refined for landscape-only tablet use.
- If this repository will be shared publicly, it is worth moving release APKs into a dedicated `releases/` or `dist/` folder to keep build artifacts separate from source output.
