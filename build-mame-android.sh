#!/usr/bin/env bash
# =============================================================================
#  build-mame-android.sh  --  build libMAME4droid.so for Android (arm64-v8a)
# =============================================================================
#
#  Downloads a MAME source release, applies the MAME4droid overlay that ships
#  with this repository (25 source patches + the "myosd" OSD layer), and
#  compiles libMAME4droid.so straight into the app's jniLibs folder.
#
#  Re-usable for any MAME release tag: 0289 (current), 0290, 0291, ...
#
#  Run it from WSL Ubuntu:
#
#      cd /mnt/c/MAME4droid-Current
#      ./build-mame-android.sh                 # build MAME 0289 (current)
#      ./build-mame-android.sh 0290            # build MAME 0290 (when released)
#      ./build-mame-android.sh 0290 -j8        # more parallel jobs
#      ./build-mame-android.sh --setup-only    # prepare only, do not compile
#
#  Options:
#      -v, --version N   MAME version number, e.g. 0289    (default: 0289)
#      -j, --jobs N      parallel compile jobs             (default: 4)
#          --ndk PATH    Android NDK (Linux) root
#                        (default: $HOME/work/android-ndk-r28c)
#          --setup-only  fetch / extract / patch / configure, then stop
#          --clean       wipe build/ first (full recompile)
#          --rebuild     delete and re-extract the MAME source tree
#          --repatch     force re-apply patches + reconfigure
#          --strip       strip the .so with llvm-strip  (DEFAULT; 532 -> 399 MiB,
#                        keeps an unstripped copy at libMAME4droid.unstripped.so)
#          --no-strip    keep the unstripped .so in jniLibs (532 MiB, debuggable)
#          -h, --help        show this help
#
#  Environment overrides:
#      MAME4DROID_REPO   repository root    (default: /mnt/c/MAME4droid-Current)
#      MAME_WORK         working directory  (default: $HOME/work)
#      ANDROID_NDK_HOME  Android NDK root   (overridden by --ndk)
#
#  Notes:
#    * Requires the LINUX NDK (not the one bundled with the Windows SDK).
#    * The Android Studio build (assembleDebug) does NOT build this .so;
#      it only packages whatever is in app/src/main/jniLibs/arm64-v8a/.
#    * .so files are git-ignored, so run this script after a fresh clone.
# =============================================================================

set -euo pipefail

# --------------------------------------------------------------------- defaults
MAME4DROID_REPO="${MAME4DROID_REPO:-/mnt/c/MAME4droid-Current}"
MAME_WORK="${MAME_WORK:-$HOME/work}"
NDK="${ANDROID_NDK_HOME:-$HOME/work/android-ndk-r28c}"
VERSION="0289"
JOBS="4"
SETUP_ONLY=0
DO_CLEAN=0
DO_REBUILD=0
DO_REPATCH=0
DO_STRIP=1

# ---------------------------------------------------------------------- helpers
C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_YLW=$'\033[33m'; C_CYN=$'\033[1;36m'; C_OFF=$'\033[0m'
hdr()  { printf '\n%s==> %s%s\n' "$C_CYN" "$*" "$C_OFF"; }
ok()   { printf '%s  ok%s  %s\n' "$C_GRN" "$C_OFF" "$*"; }
warn() { printf '%swarn%s %s\n' "$C_YLW" "$C_OFF" "$*"; }
die()  { printf '%sERROR:%s %s\n' "$C_RED" "$C_OFF" "$*" >&2; exit 1; }

usage() {
    # print the header comment block: line 3 up to (not including) the closing
    # "# ===" rule, so the help text stays correct if the header changes.
    awk 'NR<3 { next } /^# =+$/ { n++; if (n==2) exit } { sub(/^# ?/, ""); print }' "$0"
    exit 0
}

norm_version() {
    local v="${1//[!0-9]/}"
    [ -n "$v" ] || die "invalid version '$1' (expected something like 0289)"
    printf '%04d' "$((10#$v))"
}

# ------------------------------------------------------------------- parse args
while [ $# -gt 0 ]; do
    case "$1" in
        -v|--version) VERSION="${2:?--version needs a value}"; shift 2 ;;
        -j|--jobs)    JOBS="${2:?--jobs needs a value}";       shift 2 ;;
        --ndk)        NDK="${2:?--ndk needs a path}";          shift 2 ;;
        --setup-only) SETUP_ONLY=1; shift ;;
        --clean)      DO_CLEAN=1;   shift ;;
        --rebuild)    DO_REBUILD=1; shift ;;
        --repatch)    DO_REPATCH=1; shift ;;
        --strip)      DO_STRIP=1;   shift ;;
        --no-strip)   DO_STRIP=0;   shift ;;
        -h|--help)    usage ;;
        [0-9]*)       VERSION="$1"; shift ;;      # bare version number
        *)            die "unknown argument: $1  (try --help)" ;;
    esac
done

VERSION="$(norm_version "$VERSION")"

# --------------------------------------------------------------- derived paths
MAMESRC="$MAME_WORK/mame-mame$VERSION"
TARBALL="$MAME_WORK/mame$VERSION.tar.gz"
URL="https://codeload.github.com/mamedev/mame/tar.gz/refs/tags/mame$VERSION"
JNI_OUT="$MAME4DROID_REPO/android-MAME4droid/app/src/main/jniLibs/arm64-v8a"
OSD_SRC="$MAME4DROID_REPO/src/osd/myosd"
NDK_PREBUILT="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
NDK_CLANG="$NDK_PREBUILT/bin/clang"
PROJDIR="$MAMESRC/build/projects/myosd/mame/gmake-android-arm64"
MARKER="$MAMESRC/.mame4droid-prepared"
LOG="$MAME_WORK/mame-build-$VERSION.log"
SO="$JNI_OUT/libMAME4droid.so"

# ------------------------------------------------------------------- preflight
hdr "Configuration"
printf '  MAME version : %s\n' "$VERSION"
printf '  MAME source  : %s\n' "$MAMESRC"
printf '  Repository   : %s\n' "$MAME4DROID_REPO"
printf '  NDK          : %s\n' "$NDK"
printf '  Output .so   : %s\n' "$SO"
printf '  Jobs         : %s\n' "$JOBS"

[ -d "$MAME4DROID_REPO" ] || die "repository not found: $MAME4DROID_REPO"
[ -d "$OSD_SRC" ]         || die "myosd overlay not found: $OSD_SRC"
[ -x "$NDK_CLANG" ]       || die "Linux NDK clang not found: $NDK_CLANG
     Install the Linux NDK r28c, or pass --ndk /path/to/android-ndk-rXX"
command -v make    >/dev/null 2>&1 || die "make not found"
command -v python3 >/dev/null 2>&1 || die "python3 not found"
command -v tar     >/dev/null 2>&1 || die "tar not found"
export ANDROID_NDK_HOME="$NDK"

# =============================================================== 1. fetch =====
hdr "1/7  Fetch MAME $VERSION source"
mkdir -p "$MAME_WORK"
if [ ! -f "$TARBALL" ]; then
    echo "  downloading $URL"
    curl -fL --retry 3 --retry-delay 3 -o "$TARBALL.part" "$URL" \
        || die "download failed -- does tag mame$VERSION exist?  See https://github.com/mamedev/mame/tags"
    mv "$TARBALL.part" "$TARBALL"
fi
ok "tarball: $TARBALL ($(du -h "$TARBALL" | cut -f1))"

# ============================================================= 2. extract =====
hdr "2/7  Extract source"
if [ "$DO_REBUILD" = 1 ] || [ ! -d "$MAMESRC" ]; then
    rm -rf "$MAMESRC"
    echo "  extracting..."
    tar xzf "$TARBALL" -C "$MAME_WORK"
    [ -d "$MAMESRC" ] || die "expected '$MAMESRC' after extraction"
    rm -f "$MARKER"
    ok "extracted to $MAMESRC"
else
    ok "source tree already present (use --rebuild to re-extract)"
fi

# ============================================================= 3. overlay =====
hdr "3/7  Overlay the myosd OSD"
mkdir -p "$MAMESRC/src/osd/myosd"
# Sync instead of "rm -rf + cp": rsync -a leaves files whose content is unchanged
# completely alone (same mtime, no rewrite), so make keeps the OSD objects and
# only recompiles what really changed.  --delete mirrors the overlay exactly but
# must skip the generated header below, or it would be pruned on every run.
rsync -a --delete --exclude=myosdprefix.h "$OSD_SRC/" "$MAMESRC/src/osd/myosd/"
ok "myosd files in sync: $(find "$MAMESRC/src/osd/myosd" -type f | wc -l)"

# myosdprefix.h is GENERATED by step 4 (a verbatim copy of src/osd/sdl/sdlprefix.h),
# not shipped in the overlay, and step 4 only runs when the prepared marker is
# absent -- so the sync above must not delete it, and we must recreate it if it
# is missing.  It is a *forced include* for every OSD translation unit, so its
# mtime decides whether make rebuilds the whole OSD: only rewrite it when the
# content actually differs, and preserve the source mtime (-p) when we do.
SDL_PREFIX="$MAMESRC/src/osd/sdl/sdlprefix.h"
SDL_DST="$MAMESRC/src/osd/myosd/myosdprefix.h"
[ -f "$SDL_PREFIX" ] || die "MAME layout changed: $SDL_PREFIX not found"
if [ ! -f "$SDL_DST" ] || ! cmp -s "$SDL_PREFIX" "$SDL_DST"; then
    cp -p "$SDL_PREFIX" "$SDL_DST"
    ok "myosdprefix.h regenerated"
else
    ok "myosdprefix.h unchanged (mtime preserved)"
fi

# ================================================== 4. patches + configure =====
if [ -f "$MARKER" ] && [ "$DO_REPATCH" != 1 ]; then
    hdr "4/7  Patches + configure (already done, skipping)"
    ok "delete '$MARKER' or pass --repatch to redo"
else
    hdr "4/7  Apply MAME4droid patches"
    export MAMESRC MAME4DROID_REPO
    python3 - <<'PY_PATCHES'
import glob, os, subprocess, sys

MAME = os.environ["MAMESRC"]
OVER = os.environ["MAME4DROID_REPO"]

SUBDIRS = [
    "src/emu",
    "src/devices/cpu/psx",
    "src/devices/video",
    "src/frontend/mame",
    "src/frontend/mame/ui",
    "src/mame/capcom",
    "src/mame/namco",
    "src/mame/igs",
    "src/mame/snk",
    "src/osd/modules/lib",
]

total = ok = skipped = 0
failed = []
for sub in SUBDIRS:
    dst = os.path.join(MAME, sub)
    for p in sorted(glob.glob(os.path.join(OVER, sub, "*.patch"))):
        total += 1
        with open(p) as f:
            r = subprocess.run(
                ["patch", "-p1", "-N", "--fuzz=3", "-d", dst],
                stdin=f, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out = r.stdout.decode("utf-8", "replace")
        # a re-applied patch leaves a .rej file behind; clear it either way
        for rej in glob.glob(os.path.join(dst, "*.rej")):
            os.remove(rej)
        name = os.path.relpath(p, OVER)
        if "FAILED" in out or "malformed" in out or "can't find file" in out:
            failed.append((name, out))
        elif r.returncode == 0:
            ok += 1
        elif "previously applied" in out or "Reversed" in out:
            skipped += 1        # already applied -> fine (idempotent re-run)
        else:
            failed.append((name, out))

print("  patches applied: %d/%d  (already applied: %d)" % (ok, total, skipped))
for name, out in failed:
    print("  FAILED: %s" % name)
    for line in out.strip().splitlines()[-8:]:
        print("      " + line)
if failed:
    print()
    print("  Some patches did not apply cleanly. This normally means upstream MAME")
    print("  changed those files between versions. Fix the .patch file(s) under")
    print("  %s/ and re-run." % OVER)
    sys.exit(1)
PY_PATCHES

    hdr "4/7  Configure the myosd build"
    export JNI_OUT
    python3 - <<'PY_CONFIGURE'
import os, sys

MAME = os.environ["MAMESRC"]
JNI  = os.environ["JNI_OUT"]
OSD  = os.path.join(MAME, "scripts/src/osd")

def rd(p):
    with open(p, encoding="utf-8", errors="surrogateescape") as f:
        return f.read()

def wr(p, s):
    with open(p, "w", encoding="utf-8", errors="surrogateescape") as f:
        f.write(s)

# ------------------------------------------------------------- myosdprefix.h
src = os.path.join(MAME, "src/osd/sdl/sdlprefix.h")
if not os.path.isfile(src):
    sys.exit("ERROR: %s not found -- MAME layout changed, update this script" % src)
wr(os.path.join(MAME, "src/osd/myosd/myosdprefix.h"), rd(src))
print("  myosdprefix.h    written")

# ------------------------------------------------------------- myosd_cfg.lua
myosd_cfg = r'''-- license:BSD-3-Clause
-- MAME4droid "myosd" OSD configuration (Android)

dofile('modules.lua')

forcedincludes {
	MAME_DIR .. "src/osd/myosd/myosdprefix.h"
}

defines {
	"OSD_DROID",     -- selects the Android module list in osdobj_common.cpp
	"SDLMAME_UNIX",  -- posix code paths (prefix header derives SDLMAME_LINUX/SDLMAME_ANDROID)
}

if _OPTIONS["targetos"]~="android" then
	error("myosd is the Android OSD; use targetos=android")
end

-- myosd supplies its own font/render/sound/input/monitor providers
_OPTIONS["USE_QTDEBUG"] = "0"
_OPTIONS["NO_USE_PORTAUDIO"] = "1"
_OPTIONS["NO_USE_MIDI"] = "1"
'''
wr(os.path.join(OSD, "myosd_cfg.lua"), myosd_cfg)
print("  myosd_cfg.lua    written")

# ------------------------------------------------------------- myosd.lua
myosd_lua = r'''-- license:BSD-3-Clause
-- copyright-holders:MAMEdev Team
---------------------------------------------------------------------------
--  myosd.lua
--  Build rules for the MAME4droid "myosd" OSD layer (Android only).
---------------------------------------------------------------------------

-- The standard OSD scripts (sdl.lua etc.) set these globals, and src/3rdparty.lua
-- is dofile()d right after the OSD script, so "unix" must be set here or expat
-- (and anything else keyed on BASE_TARGETOS) loses its posix defines.
BASE_TARGETOS       = "unix"
SDLOS_TARGETOS      = "unix"

dofile("modules.lua")

function maintargetosdoptions(_target,_subtarget)
	osdmodulestargetconf()
end

project ("qtdbg_" .. _OPTIONS["osd"])
	uuid (os.uuid("qtdbg_" .. _OPTIONS["osd"]))
	kind (LIBTYPE)
	dofile("myosd_cfg.lua")

	-- debugqt.cpp is always part of this project (qtdebuggerbuild()) and it
	-- includes debug_module.h -> osdepend.h before its USE_QTDEBUG guard, so
	-- the include paths are needed even when the Qt debugger is disabled.
	includedirs {
		MAME_DIR .. "src/emu",
		MAME_DIR .. "src/devices",
		MAME_DIR .. "src/osd",
		MAME_DIR .. "src/lib",
		MAME_DIR .. "src/lib/util",
		MAME_DIR .. "src/osd/modules/render",
		MAME_DIR .. "3rdparty",
	}
	qtdebuggerbuild()

project ("osd_" .. _OPTIONS["osd"])
	uuid (os.uuid("osd_" .. _OPTIONS["osd"]))
	kind (LIBTYPE)

	dofile("myosd_cfg.lua")

	includedirs {
		MAME_DIR .. "src/emu",
		MAME_DIR .. "src/devices",
		MAME_DIR .. "src/osd",
		MAME_DIR .. "src/lib",
		MAME_DIR .. "src/lib/util",
		MAME_DIR .. "src/osd/modules/file",
		MAME_DIR .. "src/osd/modules/render",
		MAME_DIR .. "src/osd/modules/input",
		MAME_DIR .. "3rdparty",
		MAME_DIR .. "src/osd/myosd",
		MAME_DIR .. "src/osd/myosd/droid",
		MAME_DIR .. "src/osd/myosd/file",
		MAME_DIR .. "src/osd/myosd/modules",
		MAME_DIR .. "src/osd/myosd/netplay",
		MAME_DIR .. "src/osd/myosd/renderer",
		MAME_DIR .. "src/frontend/mame",
	}

	files {
		MAME_DIR .. "src/osd/osdepend.h",
		MAME_DIR .. "src/osd/modules/osdwindow.cpp",
		MAME_DIR .. "src/osd/modules/osdwindow.h",

		-- myosd core
		MAME_DIR .. "src/osd/myosd/input.cpp",
		MAME_DIR .. "src/osd/myosd/myosd.h",
		MAME_DIR .. "src/osd/myosd/myosd_core.h",
		MAME_DIR .. "src/osd/myosd/myosdmain.cpp",
		MAME_DIR .. "src/osd/myosd/myosdopts.cpp",
		MAME_DIR .. "src/osd/myosd/myosdopts.h",
		MAME_DIR .. "src/osd/myosd/myosd_save_hacks.cpp",
		MAME_DIR .. "src/osd/myosd/myosd_save_hacks.h",
		MAME_DIR .. "src/osd/myosd/myosd_slim_state.cpp",
		MAME_DIR .. "src/osd/myosd/myosd_slim_state.h",
		MAME_DIR .. "src/osd/myosd/video.cpp",
		MAME_DIR .. "src/osd/myosd/window.cpp",
		MAME_DIR .. "src/osd/myosd/window.h",

		-- myosd file access (posix fork)
		MAME_DIR .. "src/osd/myosd/file/posixdir.cpp",
		MAME_DIR .. "src/osd/myosd/file/posixfile.cpp",
		MAME_DIR .. "src/osd/myosd/file/posixfile.h",
		MAME_DIR .. "src/osd/myosd/file/posixptty.cpp",
		MAME_DIR .. "src/osd/myosd/file/posixsocket.cpp",

		-- myosd modules (registered by osdobj_common.cpp with OSD_DROID)
		MAME_DIR .. "src/osd/myosd/modules/drawmyosd.cpp",
		MAME_DIR .. "src/osd/myosd/modules/font_myosd.cpp",
		MAME_DIR .. "src/osd/myosd/modules/input_myosd.cpp",
		MAME_DIR .. "src/osd/myosd/modules/monitor_myosd.cpp",
		MAME_DIR .. "src/osd/myosd/modules/myosd_sound.cpp",

		-- myosd renderers
		MAME_DIR .. "src/osd/myosd/renderer/filter_shader.cpp",
		MAME_DIR .. "src/osd/myosd/renderer/filter_shader.h",
		MAME_DIR .. "src/osd/myosd/renderer/gles1_renderer.cpp",
		MAME_DIR .. "src/osd/myosd/renderer/gles1_renderer.h",
		MAME_DIR .. "src/osd/myosd/renderer/gles3_renderer.cpp",
		MAME_DIR .. "src/osd/myosd/renderer/gles3_renderer.h",
		MAME_DIR .. "src/osd/myosd/renderer/gl_utils.hxx",

		-- myosd netplay
		MAME_DIR .. "src/osd/myosd/netplay/myosd_netplay.cpp",
		MAME_DIR .. "src/osd/myosd/netplay/myosd_netplay.h",
		MAME_DIR .. "src/osd/myosd/netplay/netplay.cpp",
		MAME_DIR .. "src/osd/myosd/netplay/netplay.h",
		MAME_DIR .. "src/osd/myosd/netplay/skt_netplay.cpp",
		MAME_DIR .. "src/osd/myosd/netplay/skt_netplay.h",

		-- myosd android platform layer
		MAME_DIR .. "src/osd/myosd/droid/droid_font.cpp",
		MAME_DIR .. "src/osd/myosd/droid/myosd_droid.cpp",
		MAME_DIR .. "src/osd/myosd/droid/myosd_droid.h",
		MAME_DIR .. "src/osd/myosd/droid/myosd_platform.h",
		MAME_DIR .. "src/osd/myosd/droid/myosd_saf.h",
		MAME_DIR .. "src/osd/myosd/droid/opensl_snd.cpp",
		MAME_DIR .. "src/osd/myosd/droid/opensl_snd.h",

		-- minimal stock modules required by the OSD_DROID registration list
		MAME_DIR .. "src/osd/modules/debugger/debug_module.h",
		MAME_DIR .. "src/osd/modules/debugger/none.cpp",
		MAME_DIR .. "src/osd/modules/font/font_module.h",
		MAME_DIR .. "src/osd/modules/font/font_none.cpp",
		MAME_DIR .. "src/osd/modules/input/input_module.h",
		MAME_DIR .. "src/osd/modules/input/input_none.cpp",
		MAME_DIR .. "src/osd/modules/midi/midi_module.h",
		MAME_DIR .. "src/osd/modules/midi/none.cpp",
		MAME_DIR .. "src/osd/modules/monitor/monitor_common.cpp",
		MAME_DIR .. "src/osd/modules/monitor/monitor_common.h",
		MAME_DIR .. "src/osd/modules/monitor/monitor_module.h",
		MAME_DIR .. "src/osd/modules/netdev/netdev_module.h",
		MAME_DIR .. "src/osd/modules/netdev/none.cpp",
		MAME_DIR .. "src/osd/modules/output/none.cpp",
		MAME_DIR .. "src/osd/modules/output/output_module.h",
		MAME_DIR .. "src/osd/modules/render/drawnone.cpp",
		MAME_DIR .. "src/osd/modules/render/render_module.h",
		MAME_DIR .. "src/osd/modules/sound/none.cpp",
		MAME_DIR .. "src/osd/modules/sound/sound_module.cpp",
		MAME_DIR .. "src/osd/modules/sound/sound_module.h",
	}

project ("ocore_" .. _OPTIONS["osd"])
	uuid (os.uuid("ocore_" .. _OPTIONS["osd"]))
	kind (LIBTYPE)

	removeflags {
		"SingleOutputDir",
	}

	dofile("myosd_cfg.lua")

	includedirs {
		MAME_DIR .. "src/emu",
		MAME_DIR .. "src/devices",
		MAME_DIR .. "src/osd",
		MAME_DIR .. "src/lib",
		MAME_DIR .. "src/lib/util",
		MAME_DIR .. "3rdparty",
		MAME_DIR .. "src/osd/myosd",
		MAME_DIR .. "src/osd/myosd/droid",
	}

	files {
		MAME_DIR .. "src/osd/osdcore.cpp",
		MAME_DIR .. "src/osd/osdcore.h",
		MAME_DIR .. "src/osd/osdfile.h",
		MAME_DIR .. "src/osd/strconv.cpp",
		MAME_DIR .. "src/osd/strconv.h",
		MAME_DIR .. "src/osd/osdsync.cpp",
		MAME_DIR .. "src/osd/osdsync.h",
		MAME_DIR .. "src/osd/watchdog.cpp",
		MAME_DIR .. "src/osd/watchdog.h",
		MAME_DIR .. "src/osd/modules/osdmodule.cpp",
		MAME_DIR .. "src/osd/modules/osdmodule.h",
		MAME_DIR .. "src/osd/modules/lib/osdobj_common.cpp",
		MAME_DIR .. "src/osd/modules/lib/osdobj_common.h",
		MAME_DIR .. "src/osd/modules/lib/osdlib.h",

		-- myosd fork of osdlib_unix.cpp (provides every osdlib symbol)
		MAME_DIR .. "src/osd/myosd/droid/osdlib.cpp",

		MAME_DIR .. "src/osd/interface/audio.cpp",
		MAME_DIR .. "src/osd/interface/audio.h",
		MAME_DIR .. "src/osd/interface/inputcode.h",
		MAME_DIR .. "src/osd/interface/inputdev.h",
		MAME_DIR .. "src/osd/interface/inputfwd.h",
		MAME_DIR .. "src/osd/interface/inputman.h",
		MAME_DIR .. "src/osd/interface/inputseq.cpp",
		MAME_DIR .. "src/osd/interface/inputseq.h",
		MAME_DIR .. "src/osd/interface/midiport.h",
		MAME_DIR .. "src/osd/interface/nethandler.cpp",
		MAME_DIR .. "src/osd/interface/nethandler.h",
		MAME_DIR .. "src/osd/interface/output.h",
		MAME_DIR .. "src/osd/interface/uievents.h",
	}
'''
wr(os.path.join(OSD, "myosd.lua"), myosd_lua)
print("  myosd.lua        written")

# ------------------------------------------------------------- makefile
mp = os.path.join(MAME, "makefile")
s = rd(mp)
changed = False

guard = "ifndef SDL_INSTALL_ROOT\n\t$(error SDL_INSTALL_ROOT is not set)\nendif\n"
if guard in s:
    s = s.replace(guard, "# (myosd: SDL_INSTALL_ROOT not required)\n", 1)
    changed = True

if "--osd=$(OSD)" in s:
    pass
elif "--osd=sdl" in s:
    s = s.replace("--osd=sdl", "--osd=$(OSD)")
    changed = True
else:
    sys.exit("ERROR: makefile: neither '--osd=sdl' nor '--osd=$(OSD)' found")

if "$(PROJECTDIR_SDL)" in s:
    s = s.replace("$(PROJECTDIR_SDL)", "$(PROJECTDIR)")
    changed = True

if changed:
    wr(mp, s)
    print("  makefile         patched")
else:
    print("  makefile         already patched")

# ------------------------------------------------------------- main.lua
mlp = os.path.join(MAME, "scripts/src/main.lua")
s = rd(mlp)

if 'targetname "MAME4droid"' in s and "--no-undefined" in s and JNI in s \
        and '"bgfx",' not in s:
    # Older runs of this script patched in "GLESv2".  The myosd GLES3 renderer
    # (gles3_renderer.cpp) needs the ES 3.0 entry points, which the NDK only
    # ships in libGLESv3.so -- libGLESv2.so exports ES 2.0 only.  libGLESv3.so
    # is a superset (411 defs vs 202), so replacing is safe and matches what
    # the upstream libMAME4droid.so links against.
    if '"GLESv2",' in s:
        s = s.replace('"GLESv2",', '"GLESv3",')
        wr(mlp, s)
        print("  main.lua         upgraded GLESv2 -> GLESv3")
    else:
        print("  main.lua         already patched")
else:
    # (a) android shared-library naming + link flags
    old_cfg = '''\tconfiguration { "android*" }
\t\ttargetprefix "lib"
\t\ttargetname "main"
\t\ttargetextension ".so"
\t\tlinkoptions {
\t\t\t"-shared",
\t\t\t"-Wl,-soname,libmain.so"
\t\t}
\t\tlinks {
\t\t\t"EGL",
\t\t\t"GLESv1_CM",
\t\t\t"GLESv2",
\t\t\t"SDL2",
\t\t}'''
    new_cfg = '''\tconfiguration { "android*" }
\t\ttargetprefix "lib"
\t\ttargetname "MAME4droid"
\t\ttargetextension ".so"
\t\tlinkoptions {
\t\t\t"-shared",
\t\t\t"-Wl,-soname,libMAME4droid.so",
\t\t\t"-Wl,--no-undefined",
\t\t}
\t\tlinks {
\t\t\t"EGL",
\t\t\t"GLESv1_CM",
\t\t\t"GLESv3",
\t\t\t"log",
\t\t\t"OpenSLES",
\t\t}'''
    if old_cfg not in s:
        sys.exit("ERROR: main.lua: android configuration block not found/unchanged\n"
                 "       MAME changed scripts/src/main.lua -- update this script")
    s = s.replace(old_cfg, new_cfg, 1)

    # (b) do not link the bgfx stack; it is not built for myosd
    old_links = '''\tlinks {
\t\t"bgfx",
\t\t"bimg",
\t\t"bx",
\t\t"ocore_" .. _OPTIONS["osd"],
\t}'''
    new_links = '''\tlinks {
\t\t"ocore_" .. _OPTIONS["osd"],
\t}'''
    if old_links not in s:
        sys.exit("ERROR: main.lua: bgfx links block not found -- update this script")
    s = s.replace(old_links, new_links, 1)

    # (c) output straight into the app's jniLibs + copy libc++_shared.so
    start = s.index('\tif _OPTIONS["targetos"]=="android" then\n')
    tail  = '\telse\n\t\tif _OPTIONS["SEPARATE_BIN"]~="1" then\n\t\t\ttargetdir(MAME_DIR)\n\t\tend\n\tend'
    end_idx = s.index(tail, start)
    new_block = (
        '\tif _OPTIONS["targetos"]=="android" then\n'
        '\t\ttargetsuffix ""\n'
        '\t\tif _OPTIONS["SEPARATE_BIN"]~="1" then\n'
        '\t\t\ttargetdir("%s")\n'
        '\t\t\tos.copyfile(androidToolchainRoot() .. "/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so", "%s/libc++_shared.so")\n'
        '\t\tend\n' % (JNI, JNI)
    )
    s = s[:start] + new_block + s[end_idx:]

    wr(mlp, s)
    print("  main.lua         patched")

print("  configure done")
PY_CONFIGURE

    touch "$MARKER"
    ok "marked as prepared ($MARKER)"
fi

# ============================================================== 5. symlink =====
hdr "5/7  Link app headers into the source tree"
if [ -e "$MAMESRC/android-MAME4droid" ] && [ ! -L "$MAMESRC/android-MAME4droid" ]; then
    rm -rf "$MAMESRC/android-MAME4droid"
fi
ln -sfnT "$MAME4DROID_REPO/android-MAME4droid" "$MAMESRC/android-MAME4droid"
ok "android-MAME4droid -> $MAME4DROID_REPO/android-MAME4droid"

if [ "$SETUP_ONLY" = 1 ]; then
    hdr "Setup only -- stopping here"
    ok "run again without --setup-only to compile"
    exit 0
fi

# ============================================================= 6. generate =====
hdr "6/7  Generate (layouts, version, project files)"
cd "$MAMESRC"

# --- Native ARM64 DRC -------------------------------------------------------
# Upstream's android-arm64 rule hard-codes --NOASM=1, which forces the C DRC
# backend (drcbe_c) and drops drcbearm64.cpp.  Remove it (idempotent).
# NOASM=0 is also passed on the make command lines below.
sed -i '/--targetos=android --PLATFORM=arm64 --NOASM=1/ s/ --NOASM=1//' makefile

if [ "$DO_CLEAN" = 1 ]; then
    warn "--clean: removing $MAMESRC/build"
    rm -rf "$MAMESRC/build"
fi

CLV="$("$NDK_CLANG" -dumpversion 2>/dev/null)"
[ -n "$CLV" ] || die "could not query clang version from $NDK_CLANG"
echo "  NDK clang version: $CLV"
echo "  - layouts / version / translations ..."
make OSD=myosd SUBTARGET=mame CONFIG=release NOASM=0 generate -j"$JOBS"
echo "  - genie project files ..."
make OSD=myosd SUBTARGET=mame CONFIG=release NOASM=0 CLANG_VERSION="$CLV" \
     "build/projects/myosd/mame/gmake-android-arm64/Makefile"
[ -f "$PROJDIR/Makefile" ] || die "project Makefile was not generated: $PROJDIR/Makefile"
ok "generated: $PROJDIR/Makefile"

if grep -q "MAME_NOASM" "$PROJDIR/emu.make"; then
    die "MAME_NOASM is still defined: C-only DRC backend. Check the android-arm64 rule in makefile."
fi
grep -lq "drcbearm64" "$PROJDIR"/*.make \
    || die "drcbearm64.cpp is not in the project: the native ARM64 DRC would be missing."
ok "native ARM64 DRC enabled"

# ============================================================== 7. compile =====
hdr "7/7  Compile libMAME4droid.so (log: $LOG)"
echo "  precompile headers..."
make -C "$PROJDIR" config=release precompile 2>&1 | tee "$LOG"
echo "  building 'mame' ..."
make -C "$PROJDIR" config=release mame -j"$JOBS" 2>&1 | tee -a "$LOG"

# ================================================================ report ======
hdr "Result"
[ -f "$SO" ] || die "libMAME4droid.so was not produced -- see $LOG"

# ----------------------------------------------------------------------- strip
# jniLibs is packaged into the APK verbatim, so the unstripped copy MUST be
# written elsewhere (the repo root); two .so files in the same jniLibs ABI
# folder would both end up in the APK.
if [ "$DO_STRIP" = 1 ]; then
    STRIP="$NDK_PREBUILT/bin/llvm-strip"
    if [ -x "$STRIP" ]; then
        UNSTRIPPED="$MAME4DROID_REPO/libMAME4droid.unstripped.so"
        before=$(stat -c %s "$SO")
        cp -f "$SO" "$UNSTRIPPED"
        "$STRIP" --strip-all "$SO"
        after=$(stat -c %s "$SO")
        ok "stripped: $before -> $after bytes (saved $((before - after)) bytes)"
        ok "unstripped copy (for ndk-stack/addr2line): $UNSTRIPPED"
    else
        warn "llvm-strip not found at $STRIP -- not stripping"
    fi
fi

ls -la "$SO"
awk -v b="$(stat -c %s "$SO")" 'BEGIN{printf "  size: %d bytes = %.2f MiB\n", b, b/1048576}'

# --------------------------------------------------- verify the JNI load contract
# The app's libmame4droid-jni.so dlopen()s libMAME4droid.so at runtime and
# dlsym()s these symbols; a missing one means a black screen at startup.
NM="$NDK_PREBUILT/bin/llvm-nm"
JNIC="$MAME4DROID_REPO/android-MAME4droid/app/src/main/jni/mame4droid-jni.c"
if [ -x "$NM" ] && [ -f "$JNIC" ]; then
    miss=0; total=0
    while read -r sym; do
        [ -n "$sym" ] || continue
        total=$((total + 1))
        if [ "$("$NM" -D --defined-only "$SO" 2>/dev/null | grep -c " $sym\$" || true)" -eq 0 ]; then
            warn "MISSING export: $sym"
            miss=$((miss + 1))
        fi
    done < <(grep -oE 'dlsym\(libdl, *"[A-Za-z0-9_]+"' "$JNIC" \
             | sed 's/.*"\(.*\)"/\1/' | sort -u)
    if [ "$miss" -eq 0 ]; then
        ok "all $total dlsym symbols used by mame4droid-jni.c are exported"
    else
        die "$miss of $total dlsym symbols are missing -- the app will not start"
    fi
fi

READELF="$NDK_PREBUILT/bin/llvm-readelf"
if [ -x "$READELF" ]; then
    echo "  DT_NEEDED:"
    "$READELF" -d "$SO" 2>/dev/null | grep NEEDED | sed 's/^/    /'
fi

ok "done. Now build/run the app in Android Studio:"
echo "     open  $MAME4DROID_REPO/android-MAME4droid"
echo "     APK -> android-MAME4droid/app/build/outputs/apk/debug/app-debug.apk"

