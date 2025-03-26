package io.github.nahkd123.bulkstego;

import com.openstego.desktop.OpenStego;
import com.openstego.desktop.OpenStegoException;
import com.openstego.desktop.plugin.lsb.LSBConfig;
import com.openstego.desktop.plugin.lsb.LSBPlugin;

import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

public interface AlgorithmConfigurator {
	String getLabel();

	void renderImGui();

	OpenStego createOpenStego() throws OpenStegoException;

	class LSBConfigurator implements AlgorithmConfigurator {
		private static String[] encryptionAlgorithms = { "AES128", "AES256" };

		private LSBPlugin plugin;
		private String label;
		private int[] bitsPerChannel = { 3 };
		private ImInt encryptionIndex = new ImInt(0);
		private ImString password = new ImString();

		public LSBConfigurator(LSBPlugin plugin, String label) {
			this.plugin = plugin;
			this.label = label;
		}

		@Override
		public String getLabel() { return label; }

		@Override
		public void renderImGui() {
			ImGui.sliderInt("Bits per channel", bitsPerChannel, 1, 8);
			ImGui.combo("Encryption", encryptionIndex, encryptionAlgorithms);
			ImGui.inputText("Password (optional)", password, ImGuiInputTextFlags.Password);
		}

		@Override
		public OpenStego createOpenStego() throws OpenStegoException {
			plugin.resetConfig();
			LSBConfig config = plugin.getConfig();
			config.setUseEncryption(password.isNotEmpty());
			if (password.isNotEmpty()) {
				config.setEncryptionAlgorithm(encryptionAlgorithms[encryptionIndex.get()]);
				config.setPassword(password.get());
			}
			config.setUseCompression(true);
			config.setMaxBitsUsedPerChannel(bitsPerChannel[0]);
			return new OpenStego(plugin, config);
		}
	}
}
