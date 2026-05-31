# Rifted Valkyrium
This is an unofficial fork of [Unvalkyried Heavens](https://github.com/LordDarthDan/Unvalkyried-Heavens) by LordDarthDan, which in turn, is a fork of [Valkyrien Skies 1](https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies) by Triode et al.

This fork aims to fix some lingering issues with VS1 as well as continued maintenance of this mod for 1.12.2. This also means it's meant to be a drop-in replacement for Unvalkyried Heavens/VS1.

This fork also requires the [Cleanroom](https://cleanroommc.com/) modloader to work.

# Notable Differences with VS1 (and Unvalkyried Heavens)
* Bundle VS Core (aka this mod) back with [VS Control](https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies-control) and [VS World](https://www.curseforge.com/minecraft/mc-mods/valkyrien-skies-world).
* Fix the broken rudders at last
* Cleanup some of the code and segregated some stuff meant for VS Core from VS Control

# FAQ
## Will this work with worlds that originally used Valkyrien Skies/Unvalkyried Heavens?
In theory yes. All registry names have been preserved, so you could just replace VS/UV and its submods with just this and it will all work properly.

## Will this work with other mods that have integration/manually implement compatibility with Valkyrien Skies/Unvalkyried Heavens?
Potentially no. Some classes and packages have been changed, reorganized, or removed. If a mod that used to work with Valkyrien Skies/Unvalkyried Heavens no longer works properly, report it in the issues tab.

## Why not backport Valkyrien Skies 2/Sable instead?
I'm a really busy person irl, and somehow backporting what the aforementioned mods do from scratch will eat out most of my time ([I have like two other major projects mind you](https://github.com/Rift-Modding-Group)). Just building upon VS1 was the best logical course of action.

## Will this work with Celeritas?
Surprisingly yes. It works out of the box in fact.