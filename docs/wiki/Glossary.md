# 📖 Slimefun Glossary

A quick reference for terms you will see throughout Slimefun Legacy documentation and in-game.

| Term | Meaning |
| --- | --- |
| **Addon** | A separate plugin that registers new Slimefun content or integrations. |
| **Android** | A programmable Slimefun worker used for repeated world tasks. |
| **Buffer** | Temporary storage, often for energy or items, used to smooth production. |
| **Capacitor** | A Slimefun block used to store energy in an energy network. |
| **Cargo** | Slimefun's automated item-transport system. |
| **Cargo Channel** | A color-coded logical route used to separate item traffic inside Cargo. |
| **Cargo Manager** | The central controller of a classic Cargo network. |
| **Circuit Breaker** | Legacy runtime safeguard that temporarily isolates a repeatedly failing machine callback. |
| **Doctor** | Slimefun Legacy's diagnostic and conservative recovery tooling under `/sf doctor`. |
| **Electric Machine** | A powered Slimefun processor or utility block. |
| **Energy Network** | Connected Slimefun generators, storage and consumers. |
| **Enhanced Guide** | Legacy's expanded in-game Slimefun Guide experience with improved search and navigation features. |
| **GEO Resource** | A resource associated with a world's/location's GEO data rather than ordinary vanilla ore generation. |
| **Guide** | The in-game Slimefun recipe, research and category browser. |
| **Item ID / Slimefun ID** | Persistent identifier used to recognize a Slimefun item independently of its visible name. |
| **Legacy** | In this wiki, the Slimefun Legacy downstream fork maintained for modern Paper. |
| **Machine Ticker** | Repeating machine logic executed on the server over time. |
| **Multiblock** | A machine formed from a specific arrangement of multiple Minecraft blocks. |
| **Node** | A Cargo or network component that attaches, routes or connects part of a system. |
| **PDC** | Persistent Data Container; Minecraft/Paper metadata storage used by plugins. |
| **Previous Stable** | The prior validated Legacy release used as an important compatibility baseline. |
| **Protection Integration** | The layer that asks claim/region plugins whether a Slimefun action is allowed. |
| **Research** | Player progression unlock that grants access to Slimefun recipes/items. |
| **Runtime Smoke Test** | CI test that boots the built plugin on a real supported Paper server to catch startup/runtime failures. |
| **Staging Server** | A test copy/environment used before applying changes to production. |
| **Transactional** | An operation designed so inputs are not committed/lost unless outputs and other required steps can also complete safely. |
| **Undeclared Addon** | Addon with no Legacy compatibility declaration; it remains loadable but has no explicit declaration evidence. |

## Version terminology

### Core

The main Slimefun plugin that provides APIs, items, Guide systems, storage and runtime infrastructure.

### Fork

A separately maintained codebase derived from another project. Slimefun Legacy is an unofficial downstream fork of Slimefun 4.

### Upstream

The project/codebase from which a fork or dependency originates. Because Slimefun has multiple community continuations, "upstream" can depend on context.

### Candidate

The Slimefun Legacy build currently being tested for a future release.

## Performance terminology

### TPS

Ticks per second. Minecraft normally aims for 20 TPS.

### MSPT

Milliseconds per tick. This is often more useful than TPS when investigating how close a server is to falling behind.

### Profiler

Tool that records where server time is being spent. Slimefun's `/sf timings` provides Slimefun-specific information; spark is commonly used for server-wide profiling.

## Need a term added?

See **[Contributing to the Wiki](Contributing-to-the-Wiki.md)** or open a documentation issue with the term and where you encountered it.
