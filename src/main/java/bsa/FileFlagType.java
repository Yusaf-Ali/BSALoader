package bsa;

public enum FileFlagType {
	Meshes, Textures, Menus, Sounds, Voices, Shaders, Trees, Fonts, Miscellaneous;

	public static FileFlagType get(long l) {
		if ((l & 0x1) == 0x1)
			return Meshes;
		if ((l & 0x2) == 0x2)
			return Textures;
		if ((l & 0x4) == 0x4)
			return Menus;
		if ((l & 0x8) == 0x8)
			return Sounds;
		if ((l & 0x10) == 0x10)
			return Voices;
		if ((l & 0x20) == 0x20)
			return Shaders;
		if ((l & 0x40) == 0x40)
			return Trees;
		if ((l & 0x80) == 0x80)
			return Fonts;
		if ((l & 0x100) == 0x100)
			return Miscellaneous;
		return Miscellaneous;
	}
}
