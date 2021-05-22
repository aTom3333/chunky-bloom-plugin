package dev.ferrand.chunky.bloom.ui;

import dev.ferrand.chunky.bloom.BloomFilter;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.ui.DoubleAdjuster;
import se.llbit.chunky.ui.IntegerAdjuster;
import se.llbit.chunky.ui.RenderControlsFxController;
import se.llbit.chunky.ui.render.RenderControlsTab;
import se.llbit.json.JsonNumber;
import se.llbit.json.JsonObject;
import se.llbit.json.JsonValue;
import se.llbit.util.ProgressListener;
import se.llbit.util.TaskTracker;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class BloomTab extends ScrollPane implements RenderControlsTab, Initializable {
  private Scene scene;
  private RenderControlsFxController controller;

  @FXML private IntegerAdjuster bloom_blurRadius;
  @FXML private DoubleAdjuster bloom_threshold;
  @FXML private IntegerAdjuster bloom_downsamplingRatio;
  @FXML private CheckBox bloom_highlightOnly;

  BloomFilter bloom;

  public BloomTab(BloomFilter filter) {
    bloom = filter;
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/bloom-tab.fxml"));
      loader.setRoot(this);
      loader.setController(this);
      loader.load();
    } catch(IOException e) {
      throw new RuntimeException("Error while initialization of Bloom plug-in", e);
    }
  }

  void refreshPostProcessing() {
    if(scene == null)
      return;
    scene.postProcessFrame(new TaskTracker(ProgressListener.NONE));
    controller.getCanvas().forceRepaint();
  }

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    bloom_blurRadius.setName("Blur radius");
    bloom_blurRadius.makeLogarithmic();
    bloom_blurRadius.setRange(1, 500);
    bloom_blurRadius.setAndUpdate(bloom.getBlurRadius());
    bloom_blurRadius.onValueChange(newRadius -> {
      bloom.setBlurRadius(newRadius);
      PersistentSettings.settings.setInt("bloom.blurRadius", newRadius);
      saveInScene();
      refreshPostProcessing();
    });

    bloom_threshold.setName("Brightness threshold");
    bloom_threshold.makeLogarithmic();
    bloom_threshold.setRange(0, 50, 0.01);
    bloom_threshold.setAndUpdate(bloom.getThreshold());
    bloom_threshold.onValueChange(newThresh -> {
      bloom.setThreshold(newThresh);
      PersistentSettings.settings.setDouble("bloom.threshold", newThresh);
      saveInScene();
      refreshPostProcessing();
    });

    bloom_downsamplingRatio.setName("Downsampling ratio");
    bloom_downsamplingRatio.makeLogarithmic();
    bloom_downsamplingRatio.setRange(1, 64);
    bloom_downsamplingRatio.setAndUpdate(bloom.getDownSampleRatio());
    bloom_downsamplingRatio.onValueChange(newRatio -> {
      bloom.setDownSampleRatio(newRatio);
      PersistentSettings.settings.setInt("bloom.downsamplingRatio", newRatio);
      saveInScene();
      refreshPostProcessing();
    });

    bloom_highlightOnly.setSelected(bloom.isHighlightOnly());
    bloom_highlightOnly.selectedProperty().addListener((observable, oldvalue, newvalue) -> {
      bloom.setHighlightOnly(newvalue);
      refreshPostProcessing();
    });
  }

  @Override
  public void update(Scene scene) {
    JsonValue options = scene.getAdditionalData("bloomOptions");
    if(options.isObject()) {
      JsonObject optionsObj = options.object();
      int blurRadius = optionsObj.get("blurRadius").asInt(bloom.getBlurRadius());
      bloom.setBlurRadius(blurRadius);
      bloom_blurRadius.set(blurRadius);
      double threshold = optionsObj.get("threshold").asDouble(bloom.getThreshold());
      bloom.setThreshold(threshold);
      bloom_threshold.set(threshold);
      int ratio = optionsObj.get("downsamplingRatio").asInt(bloom.getDownSampleRatio());
      bloom.setDownSampleRatio(ratio);
      bloom_downsamplingRatio.set(ratio);
    }
    refreshPostProcessing();
  }

  private void saveInScene() {
    JsonObject options;
    boolean needToAdd = true;
    if(scene.getAdditionalData("bloomOptions").isObject()) {
      options = scene.getAdditionalData("bloomOptions").asObject();
      needToAdd = false;
    } else {
      options = new JsonObject();
    }

    options.set("blurRadius", new JsonNumber(bloom.getBlurRadius()));
    options.set("threshold", new JsonNumber(bloom.getThreshold()));
    options.set("downsamplingRatio", new JsonNumber(bloom.getDownSampleRatio()));

    if(needToAdd)
      scene.setAdditionalData("bloomOptions", options);
  }

  @Override
  public void setController(RenderControlsFxController controller) {
    this.controller = controller;
    scene = controller.getRenderController().getSceneManager().getScene();
  }

  @Override
  public String getTabTitle() {
    return "Bloom Options";
  }

  @Override
  public Node getTabContent() {
    return this;
  }
}
