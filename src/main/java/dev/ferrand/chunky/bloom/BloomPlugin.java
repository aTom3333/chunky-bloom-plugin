package dev.ferrand.chunky.bloom;

import dev.ferrand.chunky.bloom.ui.BloomTab;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.Plugin;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilters;
import se.llbit.chunky.ui.ChunkyFx;
import se.llbit.chunky.ui.render.RenderControlsTabTransformer;

public class BloomPlugin implements Plugin {
  @Override
  public void attach(Chunky chunky) {
    BloomFilter bloom = new BloomFilter(
            PersistentSettings.settings.getInt("bloom.downsamplingRatio", 4),
            PersistentSettings.settings.getInt("bloom.blurRadius", 4),
            PersistentSettings.settings.getDouble("bloom.threshold", 1.0)
    );
    PostProcessingFilters.addPostProcessingFilter(bloom);
    RenderControlsTabTransformer previousTransformer = chunky.getRenderControlsTabTransformer();
    chunky.setRenderControlsTabTransformer(tabs -> {
      tabs = previousTransformer.apply(tabs);
      tabs.add(new BloomTab(bloom));
      return tabs;
    });
  }

  public static void main(String[] args) {
    // Start Chunky normally with this plugin attached.
    Chunky.loadDefaultTextures();
    Chunky chunky = new Chunky(ChunkyOptions.getDefaults());
    new BloomPlugin().attach(chunky);
    ChunkyFx.startChunkyUI(chunky);
  }
}
