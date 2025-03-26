package io.github.nahkd123.bulkstego;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.openstego.desktop.OpenStego;
import com.openstego.desktop.plugin.lsb.LSBPlugin;
import com.openstego.desktop.plugin.randlsb.RandomLSBPlugin;

import imgui.ImColor;
import imgui.ImGui;
import imgui.app.Application;
import imgui.extension.imguifiledialog.ImGuiFileDialog;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;

public class BulkStego extends Application {
	public static void main(String[] args) {
		Application.launch(new BulkStego());
	}

	private AlgorithmConfigurator[] algorithms = {
		new AlgorithmConfigurator.LSBConfigurator(new RandomLSBPlugin(), "RandomLSB"),
		new AlgorithmConfigurator.LSBConfigurator(new LSBPlugin(), "LSB (Least significant bits)"),
	};
	private String[] algorithmLabels;

	private Path coverImage;
	private Path textFile;
	private ImInt algorithmIndex = new ImInt(0);
	private Path outputFolder;

	private boolean running = false;
	private AtomicInteger processed = new AtomicInteger(0);
	private int max = 0;
	private Path outputCsv;

	public BulkStego() {
		algorithmLabels = new String[algorithms.length];
		for (int i = 0; i < algorithms.length; i++) algorithmLabels[i] = algorithms[i].getLabel();
	}

	@Override
	protected void preRun() {
		try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream("Inter.ttf")) {
			byte[] fontData = new byte[fontStream.available()];
			int off = 0;
			while (off < fontData.length) off += fontStream.read(fontData, off, fontData.length - off);
			ImGui.getIO().getFonts().setFreeTypeRenderer(true);
			ImGui.getIO().getFonts().addFontFromMemoryTTF(fontData, 20f);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	@Override
	public void process() {
		ImGui.setNextWindowSize(800f, 400f, ImGuiCond.FirstUseEver);
		ImGui.setNextWindowPos(
			ImGui.getIO().getDisplaySizeX() / 2f,
			ImGui.getIO().getDisplaySizeY() / 2f,
			ImGuiCond.FirstUseEver, 0.5f, 0.5f);
		if (ImGui.begin("BulkStego")) {
			ImGui.textWrapped("Steganography in bulk for the leak-aware mindsets. If you know, you know ;)");
			ImGui.textWrapped("Select cover image and text file that contains message on each line.");

			if (ImGui.button("Choose cover image"))
				ImGuiFileDialog.openModal("browse-cover-image", "Choose cover image", ".png", ".", 1);
			ImGui.sameLine();
			ImGui.textColored(
				coverImage != null ? ImColor.rgba(ImGui.getStyle().getColor(ImGuiCol.Text)) : 0xFF7F7FFF,
				String.format("Current cover image: %s", coverImage != null ? coverImage : "<Not selected>"));

			if (ImGuiFileDialog.display("browse-cover-image", 0, 200, 400, 800, 600)) {
				if (ImGuiFileDialog.isOk()) {
					coverImage = ImGuiFileDialog.getSelection().values().stream()
						.findFirst()
						.map(p -> Paths.get(p))
						.orElse(null);
				}
				ImGuiFileDialog.close();
			}

			if (ImGui.button("Choose text file"))
				ImGuiFileDialog.openModal("browse-text-file", "Choose text file", ".txt", ".", 1);
			ImGui.sameLine();
			ImGui.textColored(
				textFile != null ? ImColor.rgba(ImGui.getStyle().getColor(ImGuiCol.Text)) : 0xFF7F7FFF,
				String.format("Current text file: %s", textFile != null ? textFile : "<Not selected>"));

			if (ImGuiFileDialog.display("browse-text-file", 0, 200, 400, 800, 600)) {
				if (ImGuiFileDialog.isOk()) {
					textFile = ImGuiFileDialog.getSelection().values().stream()
						.findFirst()
						.map(p -> Paths.get(p))
						.orElse(null);
				}
				ImGuiFileDialog.close();
			}

			ImGui.separator();
			ImGui.text("Configure OpenStego");
			ImGui.combo("Algorithm", algorithmIndex, algorithmLabels);
			algorithms[algorithmIndex.get()].renderImGui();

			ImGui.separator();
			ImGui.text("Select where to save all generated image files");
			if (ImGui.button("Choose output folder"))
				ImGuiFileDialog.openDialog("browse-output-folder", "Choose output folder", null, ".");

			ImGui.sameLine();
			ImGui.textColored(
				outputFolder != null ? ImColor.rgba(ImGui.getStyle().getColor(ImGuiCol.Text)) : 0xFF7F7FFF,
				String.format("Current output folder: %s", outputFolder != null ? outputFolder : "<Not selected>"));

			if (ImGuiFileDialog.display("browse-output-folder", 0, 200, 400, 800, 600)) {
				if (ImGuiFileDialog.isOk()) {
					outputFolder = ImGuiFileDialog.getSelection().entrySet().stream()
						.findFirst()
						.map(e -> e.getValue().substring(0, e.getValue().length() - e.getKey().length()))
						.map(p -> Paths.get(p))
						.orElse(null);
				}
				ImGuiFileDialog.close();
			}

			ImGui.beginDisabled(running || coverImage == null || textFile == null || outputFolder == null);
			if (ImGui.button("Start embed process")) beginBulkApply().thenAccept(path -> outputCsv = path);
			ImGui.text(String.format("Processed %d out of %d files", processed.get(), max));
			if (outputCsv != null) ImGui.text(String.format("CSV table written to %s", outputCsv));
			ImGui.endDisabled();
		}
		ImGui.end();
	}

	private CompletableFuture<Path> beginBulkApply() {
		try {
			AlgorithmConfigurator algo = algorithms[algorithmIndex.get()];
			byte[] cover = Files.readAllBytes(coverImage);
			String baseName = coverImage.getFileName().toString().replaceAll("\\.png$", "");
			List<String> lines = Files.readAllLines(textFile, StandardCharsets.UTF_8);
			processed.set(0);
			max = lines.size();

			List<CompletableFuture<String[]>> tasks = new ArrayList<>();
			for (int i = 0; i < lines.size(); i++) {
				String outputBase = baseName + String.format("-%08d", i + 1);
				Path outputFile = outputFolder.resolve(outputBase + ".png");
				String message = lines.get(i);
				byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

				tasks.add(CompletableFuture.supplyAsync(() -> {
					try {
						OpenStego openStego = algo.createOpenStego();
						byte[] output = openStego.embedData(
							messageBytes, outputBase + "-message.txt",
							cover, coverImage.toString(),
							outputFile.toString());
						Files.write(outputFile, output);
						processed.incrementAndGet();
						return new String[] { message, outputFile.toString() };
					} catch (Throwable t) {
						t.printStackTrace();
						return null;
					}
				}));
			}

			running = true;
			return CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]))
				.<Path>thenApply(v -> {
					Path outputCsv = outputFolder.resolve(baseName + ".csv");
					List<String> outputLines = new ArrayList<>();
					outputLines.add("Message,File");
					tasks.forEach(task -> {
						String[] entry = task.join();
						outputLines.add(entry[0] + "," + entry[1]);
					});

					try {
						Files.write(outputCsv, outputLines, StandardCharsets.UTF_8);
						return outputCsv;
					} catch (IOException e) {
						e.printStackTrace();
						return null;
					} finally {
						running = false;
					}
				});
		} catch (Throwable t) {
			t.printStackTrace();
			return CompletableFuture.completedFuture(null);
		}
	}
}
