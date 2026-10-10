#!/usr/bin/env python3
"""Exercise pinned, already-published plugin upgrades on a new disposable server.

No owner databases/worlds are read. No release, source or live-server mutation API is used.
Each job keeps one exact Paper or Purpur binary across seed/control/upgrade/restart; this tests a
plugin-set upgrade, not a Minecraft-version upgrade. Run only in an ephemeral CI workspace.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.request
import uuid
import zipfile

OWNER_NAME = "SFLTestFixture"
# Match Java UUID.nameUUIDFromBytes used by Minecraft offline-mode identity.
OWNER = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + OWNER_NAME).encode("utf-8")).digest(), version=3))
ROOT = Path(__file__).resolve().parents[1]
USER_AGENT = "Slimefun-Legacy-Published-Upgrade-Fixture/1.0 (https://github.com/wickidcow/Slimefun-Legacy)"
PURPUR_BUILD = "2646"
PHASES = ("seed", "baseline", "upgrade", "restart")


@dataclass(frozen=True)
class PublishedSet:
    version: str
    core_source: str
    bundle_source: str
    bundle_revision: int
    core_sha256: str
    core_bytes: int
    addons_sha256: str
    addons_bytes: int

    def assets(self) -> dict[str, tuple[str, str, int]]:
        return {
            "core": (f"Slimefun-Legacy{self.version}.jar", self.core_sha256, self.core_bytes),
            "addons": ("SF_Addons_1.21.11-26.3.zip", self.addons_sha256, self.addons_bytes),
        }


# These are immutable published pairs, not current branch heads or replaceable latest assets.
# The historical 4.1.69 core refresh and unchanged r112 bundle have different actual sources.
SCENARIOS = {
    "4.1.69-to-4.1.70": (
        PublishedSet(
            "4.1.69", "d430eda74525b31328660bc3d3e36e13c9869353",
            "bcd8b3a6619541df82dc5858178ef888a090e3c5", 112,
            "d179f2f677095193b6d4f7a76907ccf648444e84d270a51d24bc44da7c4ef1fc", 8618203,
            "7075b295e72685148fd2170e8af64b6e7aa6ac3580a214227fec91c8293ab267", 24297810,
        ),
        PublishedSet(
            "4.1.70", "682f26ef1c6a71c905091fb178bf4b3763bc1652",
            "682f26ef1c6a71c905091fb178bf4b3763bc1652", 125,
            "c3fc4bb3b983a285ec9c553418153aeb02a7bd9fb501e79d95320059d1dff9e7", 8647469,
            "bca8554156b85b98c985910c4bfe6673fb34f67de002145fed98ded6f1680ecc", 24310288,
        ),
    ),
    "4.1.71-to-4.1.72": (
        PublishedSet(
            "4.1.71", "f68544e47d8489704d0e9d34adc65f0114b07153",
            "f68544e47d8489704d0e9d34adc65f0114b07153", 127,
            "916b055a6917c87405078948fd00cdbb2394078248d1c9bfbbfa9c4b37106e6a", 8652946,
            "85082865585a383b67ee2e0dade4712a0d97f7a37253f9336b42f5eb0c7084cf", 24335659,
        ),
        PublishedSet(
            "4.1.72", "504db79c4ce82358c8a74968d7fbab3f4af1d017",
            "504db79c4ce82358c8a74968d7fbab3f4af1d017", 129,
            "0d589d2f5360ac5c254663ac56dd9495a494c0ea9cc5dc4110927d6ed0e8fa12", 8667716,
            "28fb328d35c6e629a8319fe4297f56c2f4304b6a57903976b278590dce61c641", 24336468,
        ),
    ),
}


def require(ok: bool, message: str) -> None:
    if not ok:
        raise ValueError(message)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def download(url: str, path: Path, sha: str, size: int | None = None) -> None:
    require(re.fullmatch(r"[0-9a-f]{64}", sha) is not None, "Missing SHA-256 for download")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    limit = size if size is not None else 150_000_000
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read(limit + 1)
    require(len(data) <= limit and (size is None or len(data) == size), f"Wrong download size: {path.name}")
    require(hashlib.sha256(data).hexdigest() == sha, f"Download hash mismatch: {path.name}")
    path.write_bytes(data)


def scalar(descriptor: str, key: str) -> str:
    found = re.findall(rf"(?m)^{re.escape(key)}:\s*([^\r\n]+)", descriptor)
    require(len(found) == 1, f"Expected exactly one descriptor {key}")
    return found[0].strip().strip("\"'")


def inspect_core(path: Path, expected: PublishedSet) -> None:
    with zipfile.ZipFile(path) as core:
        require(len(core.namelist()) == len(set(core.namelist())), "Duplicate core JAR entries")
        require(core.testzip() is None, "Core JAR CRC failure")
        properties = core.read("git.properties").decode("utf-8")
        source = re.findall(r"(?m)^git\.source\.commit=([^\r\n]*)\r?$", properties)
        require(source == [expected.core_source], "Published core source mismatch")
        descriptor = core.read("plugin.yml").decode("utf-8")
        require(scalar(descriptor, "name") == "Slimefun", "Published core plugin name mismatch")
        require(scalar(descriptor, "version") == expected.version, "Published core version mismatch")


def inspect_bundle(path: Path, expected: PublishedSet) -> tuple[dict, dict[str, bytes], str]:
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), "Duplicate ZIP entries")
        require(sum(i.file_size for i in archive.infolist()) <= 150_000_000, "Bundle exceeds fixture size limit")
        require(archive.testzip() is None, "Bundle CRC failure")
        manifest = json.loads(archive.read("SF_ADDON_MANIFEST.json"))
        records = manifest["addons"]
        require(len(records) == 45, "Published set must contain 45 addons")
        require(type(manifest["bundle_revision"]) is int
                and manifest["bundle_revision"] == expected.bundle_revision, "Unexpected bundle revision")
        require(manifest["core_source_commit"] == expected.bundle_source, "Wrong published bundle source")
        names = [r["jar"] for r in records]
        require(len(names) == len(set(names)), "Duplicate manifest JAR")
        require(set(names) == {n for n in archive.namelist() if n.endswith(".jar")}, "Manifest JAR set mismatch")
        names_seen: set[str] = set()
        jars: dict[str, bytes] = {}
        plugins: list[str] = []
        for record in records:
            name = record["jar"]
            require(re.fullmatch(r"[A-Za-z0-9_.()\-]+\.jar", name) is not None, "Unsafe JAR path")
            data = archive.read(name)
            require(hashlib.sha256(data).hexdigest() == record["sha256"], f"Addon hash mismatch: {name}")
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                descriptor_name = next((n for n in ("plugin.yml", "paper-plugin.yml", "paper-plugin.yaml")
                                        if n in jar.namelist()), None)
                require(descriptor_name is not None, f"Missing plugin descriptor: {name}")
                descriptor = jar.read(descriptor_name).decode("utf-8")
                plugin, version = scalar(descriptor, "name"), scalar(descriptor, "version")
                require(plugin.casefold() not in names_seen, "Duplicate plugin identity")
                require(version == record["version"], "Plugin version mismatch")
                names_seen.add(plugin.casefold())
                plugins.append(f"{plugin}\t{version}\n")
            jars[name] = data
        return manifest, jars, "".join(sorted(plugins))


def paper_build(minecraft: str, builds: list[dict]) -> dict:
    channels = {"STABLE", "BETA"} if minecraft == "26.3" else {"STABLE"}
    eligible = [b for b in builds if b.get("channel") in channels]
    require(bool(eligible), "No permitted Paper build; no fallback to another Minecraft version")
    # Prefer stable when it exists, otherwise use explicit 26.3 beta coverage.
    stable = [b for b in eligible if b["channel"] == "STABLE"]
    selected = max(stable or eligible, key=lambda b: b["id"])
    require(bool(selected["downloads"]["server:default"]["checksums"].get("sha256")), "Missing server checksum")
    return selected


def validate_runtime(platform: str, minecraft: str, purpur_build: str | None, scenario: str) -> None:
    require(scenario in SCENARIOS, "Unknown published upgrade scenario")
    require(platform in ("paper", "purpur"), "Unsupported server platform")
    require(minecraft in ("1.21.11", "26.2", "26.3"), "Unsupported Minecraft version")
    if platform == "paper":
        require(purpur_build is None, "A Purpur build must not be supplied for Paper")
    else:
        require(scenario == "4.1.71-to-4.1.72", "Purpur is reviewed only for the 4.1.71-to-4.1.72 scenario")
        require(minecraft == "26.3", "Purpur published-upgrade milestone is 26.3 only")
        require(purpur_build is not None and re.fullmatch(r"[0-9]+", purpur_build) is not None,
                "Purpur requires an explicit numeric reviewed build pin")
        require(purpur_build == PURPUR_BUILD, "Unreviewed Purpur build; expected pinned build 2646")


def fetch_json(url: str) -> dict | list:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def validate_purpur_metadata(build: str, metadata: dict, detail: dict) -> str:
    require(metadata.get("project") == "purpur" and metadata.get("version") == "26.3",
            "Wrong Purpur project/version metadata")
    listed = metadata.get("builds", {}).get("all", [])
    require(isinstance(listed, list) and build in [str(value) for value in listed],
            "Pinned Purpur build was not listed by the official downloads API")
    require(detail.get("project") == "purpur" and detail.get("version") == "26.3"
            and str(detail.get("build")) == build, "Wrong Purpur build metadata")
    require(detail.get("result") == "SUCCESS", "Pinned Purpur build did not succeed")
    require(detail.get("metadata", {}).get("type") == "experimental",
            "Pinned Purpur milestone metadata is not experimental")
    checksum = detail.get("md5", "")
    require(isinstance(checksum, str) and re.fullmatch(r"[0-9a-f]{32}", checksum) is not None,
            "Missing official Purpur checksum")
    return checksum


def prepare_server(server: Path, platform: str, minecraft: str, purpur_build: str | None, scenario: str) -> dict:
    validate_runtime(platform, minecraft, purpur_build, scenario)
    if platform == "paper":
        selected = paper_build(minecraft, fetch_json(
            f"https://fill.papermc.io/v3/projects/paper/versions/{minecraft}/builds"))
        asset = selected["downloads"]["server:default"]
        download(asset["url"], server / "server.jar", asset["checksums"]["sha256"], asset.get("size"))
        return {"platform": platform, "minecraft": minecraft, "build": selected["id"],
                "channel": selected["channel"], "sha256": digest(server / "server.jar")}
    base = "https://api.purpurmc.org/v2/purpur/26.3"
    metadata = fetch_json(base)
    detail = fetch_json(f"{base}/{purpur_build}")
    checksum = validate_purpur_metadata(purpur_build, metadata, detail)
    request = urllib.request.Request(f"{base}/{purpur_build}/download", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = response.read(150_000_001)
    require(0 < len(data) <= 150_000_000, "Wrong Purpur download size")
    require(hashlib.md5(data).hexdigest() == checksum, "Purpur download checksum mismatch")
    require(zipfile.is_zipfile(io.BytesIO(data)), "Purpur download is not a server JAR")
    (server / "server.jar").write_bytes(data)
    return {"platform": platform, "minecraft": minecraft, "build": purpur_build,
            "channel": "pinned-experimental", "official_md5": checksum,
            "sha256": digest(server / "server.jar")}


def compile_plugin(work: Path, core: Path, version: str) -> Path:
    build = work / "fixture-build"
    build.mkdir()
    source = ROOT / "tests/published-upgrade"
    # This is a real Paper API dependency and the actual OLD published core, not hand-written doubles.
    pom = f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>io.github.wickidcow.test</groupId><artifactId>published-upgrade-fixture</artifactId><version>1.0.0</version>
<properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
<repositories><repository><id>paper</id><url>https://repo.papermc.io/repository/maven-public/</url></repository></repositories>
<dependencies><dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>1.21.11-R0.1-SNAPSHOT</version><scope>provided</scope></dependency>
<dependency><groupId>local.fixture</groupId><artifactId>old-slimefun</artifactId><version>{version}</version><scope>system</scope><systemPath>{core}</systemPath></dependency></dependencies>
<build><sourceDirectory>{source}</sourceDirectory><resources><resource><directory>{build / 'resources'}</directory></resource></resources>
<plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version></plugin></plugins></build></project>'''
    (build / "pom.xml").write_text(pom)
    (build / "resources").mkdir()
    (build / "resources/plugin.yml").write_text(
        "name: PublishedUpgradeFixture\nversion: '1.0.0'\nmain: io.github.wickidcow.sfltest.PublishedUpgradeFixture\n"
        "api-version: '1.21.11'\ndepend: [Slimefun]\ncommands:\n  upgradefixture:\n    description: Disposable CI fixture only\n")
    with (work / "compile.log").open("w") as log:
        subprocess.run(["mvn", "--batch-mode", "--no-transfer-progress", "-f", str(build / "pom.xml"), "package"],
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=300)
    artifact = build / "target/published-upgrade-fixture-1.0.0.jar"
    require(artifact.is_file(), "Fixture compilation did not produce its test plugin")
    return artifact


def parse_result(path: Path) -> dict[str, str]:
    data = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        require("=" in line, "Malformed fixture result line")
        key, value = line.split("=", 1)
        require(key and key not in data, "Duplicate or empty fixture result key")
        data[key] = value
    require(data.get("status") == "PASS", f"Fixture did not pass: {data}")
    return data


def validate_phase_result(parsed: dict, phase: str, scenario: str, platform: str, minecraft: str) -> None:
    require(scenario in SCENARIOS and phase in PHASES, "Unknown fixture scenario/phase")
    old, new = SCENARIOS[scenario]
    expected = old.version if phase in ("seed", "baseline") else new.version
    for key, value in {"status": "PASS", "phase": phase, "scenario": scenario,
                       "old_core_version": old.version, "new_core_version": new.version,
                       "expected_core_version": expected, "core": expected, "addons_enabled": "45"}.items():
        require(parsed.get(key) == value, f"Wrong fixture {key} evidence")
    require(parsed.get("minecraft") == minecraft, "Wrong native Minecraft version evidence")
    require(parsed.get("server_name", "").casefold() == platform, "Wrong native server platform evidence")
    require(bool(parsed.get("server_version", "").strip()), "Missing native server version evidence")
    if phase != "seed":
        require(int(parsed.get("checked_items", "0")) >= 30, "Vacuous item verification")


def verify_frozen_inputs(server: Path, expected: dict[str, str]) -> None:
    for name, sha in expected.items():
        path = server / name
        require(path.is_file() and digest(path) == sha, f"Frozen fixture input changed: {name}")


def run_phase(server: Path, phase: str, scenario: str, platform: str, minecraft: str) -> dict:
    (server / "fixture-phase.txt").write_text(phase)
    log_path = server.parent / f"{phase}.console.log"
    result = server / f"fixture-{phase}-result.txt"
    require(not result.exists(), "Refusing stale phase evidence")
    process = None
    with log_path.open("w") as log:
        try:
            process = subprocess.Popen(["java", "-Xms512M", "-Xmx3G", "-Dsfl.upgrade.fixture=disposable-only",
                                        "-jar", "server.jar", "--nogui"], cwd=server, stdin=subprocess.PIPE,
                                       stdout=log, stderr=subprocess.STDOUT, text=True)
            deadline = time.monotonic() + 420
            while time.monotonic() < deadline:
                require(process.poll() is None, f"Server exited during startup: {phase}")
                if "Done (" in log_path.read_text(errors="replace"):
                    break
                time.sleep(1)
            else:
                raise TimeoutError(f"Server startup timeout: {phase}")
            time.sleep(10)
            process.stdin.write(f"upgradefixture {phase}\n")
            process.stdin.flush()
            deadline = time.monotonic() + 150
            while not result.exists() and time.monotonic() < deadline:
                require(process.poll() is None, f"Server exited during fixture: {phase}")
                time.sleep(1)
            require(result.is_file(), f"No fixture result: {phase}")
            # Keep shutdown evidence even when the fixture has failed.
            process.stdin.write("sf doctor status\nstop\n")
            process.stdin.flush()
            require(process.wait(timeout=90) == 0, f"Nonzero shutdown: {phase}")
        finally:
            if process is not None and process.poll() is None:
                try:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                    process.wait(timeout=30)
                except (OSError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=10)
    text = log_path.read_text(errors="replace")
    require(not re.search(r"Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError", text),
            f"Startup/linkage failure in {phase}")
    require("SFL_UPGRADE_FIXTURE_FAIL" not in text, f"Fixture exception in {phase}")
    parsed = parse_result(result)
    validate_phase_result(parsed, phase, scenario, platform, minecraft)
    return parsed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scenario", choices=tuple(SCENARIOS), required=True)
    parser.add_argument("--platform", choices=["paper", "purpur"], default="paper")
    parser.add_argument("--purpur-build")
    parser.add_argument("--minecraft", choices=["1.21.11", "26.2", "26.3"], required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    args = parser.parse_args()
    validate_runtime(args.platform, args.minecraft, args.purpur_build, args.scenario)
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Disposable GitHub Actions execution is required")
    old, new = SCENARIOS[args.scenario]
    work = args.work_dir.resolve()
    workspace = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
    require(work.is_relative_to(workspace / "build") and work != workspace / "build", "Work directory must be a child of workspace/build")
    # Never delete or reuse caller-supplied server/world/data directories.
    require(not work.exists(), "Work directory already exists; use a fresh disposable directory")
    work.mkdir(parents=True)
    server = work / "server"
    server.mkdir()
    plugins = server / "plugins"
    plugins.mkdir()
    evidence = {"scenario": args.scenario, "old_core_version": old.version, "new_core_version": new.version,
                "old_core_source": old.core_source, "new_core_source": new.core_source,
                "old_bundle_source": old.bundle_source, "new_bundle_source": new.bundle_source,
                "old_bundle_revision": old.bundle_revision, "new_bundle_revision": new.bundle_revision,
                "platform": args.platform, "minecraft": args.minecraft,
                "scope": "synthetic plugin-set upgrade; same server binary on all four boots", "phases": []}
    assets = {}
    for epoch, selected in (("old", old), ("new", new)):
        for kind, (name, sha, size) in selected.assets().items():
            label = epoch + "-" + kind
            target = work / (label + (".jar" if kind == "core" else ".zip"))
            download(f"https://github.com/wickidcow/Slimefun-Legacy/releases/download/v{selected.version}/{name}",
                     target, sha, size)
            assets[label] = target
        inspect_core(assets[epoch + "-core"], selected)
    baseline, old_jars, old_plugins = inspect_bundle(assets["old-addons"], old)
    candidate, new_jars, new_plugins = inspect_bundle(assets["new-addons"], new)
    require({r["repository"] for r in baseline["addons"]} == {r["repository"] for r in candidate["addons"]},
            "Upgrade changed addon membership; fixture needs explicit review")
    evidence["assets"] = {key: {"sha256": digest(value), "bytes": value.stat().st_size} for key, value in assets.items()}
    fixture = compile_plugin(work, assets["old-core"], old.version)
    shutil.copy2(fixture, plugins / "PublishedUpgradeFixture.jar")
    evidence["server"] = prepare_server(server, args.platform, args.minecraft, args.purpur_build, args.scenario)
    (server / "fixture-scenario.properties").write_text(
        f"scenario={args.scenario}\nold-core-version={old.version}\nnew-core-version={new.version}\n",
        encoding="utf-8")
    frozen_inputs = {name: digest(server / name) for name in ("server.jar", "fixture-scenario.properties")}
    evidence["frozen_inputs"] = frozen_inputs
    provider_args = ["--allow-prerelease"] if args.minecraft == "26.3" else []
    subprocess.run(["python3", str(ROOT / "scripts/prepare_worldedit_runtime.py"), "--minecraft", args.minecraft,
                    "--output", str(plugins), *provider_args], check=True, timeout=180)
    # The fixture and independently selected provider must also stay byte-identical across all boots.
    frozen_inputs.update({str(path.relative_to(server)): digest(path) for path in plugins.glob("*.jar")})
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nonline-mode=false\nlevel-name=sfl-upgrade-fixture\nlevel-type=minecraft:flat\n"
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\n'
        "max-players=1\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\n"
        "enable-query=false\nenable-rcon=false\nallow-nether=false\n")
    (server / "fixture-authorization.txt").write_text(OWNER)
    (plugins / "Slimefun").mkdir()
    (plugins / "Slimefun/config.yml").write_text("options:\n  auto-update: false\n  language: en\n  enable-translations: false\n")
    for phase in PHASES:
        epoch = "old" if phase in ("seed", "baseline") else "new"
        if phase in ("seed", "upgrade"):
            for name in set(old_jars) | set(new_jars):
                (plugins / name).unlink(missing_ok=True)
            # Removing remapping cache is test-only; do not carry stale transformed JARs across plugin upgrades.
            cache = plugins / ".paper-remapped"
            if cache.exists():
                shutil.rmtree(cache)
            for name, data in (old_jars if epoch == "old" else new_jars).items():
                (plugins / name).write_bytes(data)
            shutil.copy2(assets[epoch + "-core"], plugins / "Slimefun-Legacy.jar")
            (server / "fixture-plugins.tsv").write_text(old_plugins if epoch == "old" else new_plugins)
        before = {p.name: digest(p) for p in plugins.glob("*.jar")}
        verify_frozen_inputs(server, frozen_inputs)
        result = run_phase(server, phase, args.scenario, args.platform, args.minecraft)
        verify_frozen_inputs(server, frozen_inputs)
        after = {p.name: digest(p) for p in plugins.glob("*.jar")}
        require(before == after, "A plugin modified the frozen installed JAR set")
        result["server_sha256"] = frozen_inputs["server.jar"]
        result["scenario_sha256"] = frozen_inputs["fixture-scenario.properties"]
        evidence["phases"].append(result)
        (work / "upgrade-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
        print("PUBLISHED_UPGRADE_PHASE_PASS", args.scenario, args.platform, args.minecraft, phase, flush=True)
    evidence["status"] = "PASS"
    evidence["limitations"] = ["generated fixture, not a copied owner world or supplied historical database",
                               "representative items, not every item or dynamic addon state",
                               "no machine execution, Cargo/Networks transfer gameplay or Folia certification",
                               "acknowledged saves, not pending-write shutdown or crash durability",
                               "plugin upgrade on one fixed server binary, not a Minecraft-version upgrade"]
    (work / "upgrade-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print("PUBLISHED_UPGRADE_FIXTURE_PASS", args.scenario, args.platform, args.minecraft)


if __name__ == "__main__":
    main()
