package dev.ferrand.chunky.bloom;

import org.apache.commons.math3.util.FastMath;
import se.llbit.chunky.renderer.postprocessing.PostProcessingFilter;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.resources.BitmapImage;
import se.llbit.math.ColorUtil;
import se.llbit.util.TaskTracker;

public class BloomFilter implements PostProcessingFilter {
  private int downSampleRatio;
  private int blurRadius;
  private float[] blurKernel;
  private double threshold;
  private boolean highlightOnly = false;

  public BloomFilter(int downSampleRatio, int blurRadius, double threshold) {
    this.downSampleRatio = downSampleRatio;
    setBlurRadius(blurRadius);
    this.threshold = threshold;
  }

  public int getDownSampleRatio() {
    return downSampleRatio;
  }

  public synchronized void setDownSampleRatio(int downSampleRatio) {
    if(downSampleRatio < 1)
      downSampleRatio = 1;
    setBlurRadius((int) Math.round(blurRadius * this.downSampleRatio / (double) downSampleRatio));
    this.downSampleRatio = downSampleRatio;
  }

  public int getBlurRadius() {
    return blurRadius;
  }

  public synchronized void setBlurRadius(int blurRadius) {
    this.blurRadius = blurRadius;
    blurKernel = computeBlurKernel(blurRadius);
  }

  public double getThreshold() {
    return threshold;
  }

  public synchronized void setThreshold(double threshold) {
    this.threshold = threshold;
  }

  public boolean isHighlightOnly() {
    return highlightOnly;
  }

  public synchronized void setHighlightOnly(boolean highlightOnly) {
    this.highlightOnly = highlightOnly;
  }

  @Override
  public synchronized void processFrame(int width, int height, double[] input, BitmapImage output, double exposure, TaskTracker.Task task) {
    int reducedWidth = (width + downSampleRatio - 1) / downSampleRatio;
    int reducedHeight = (height + downSampleRatio - 1) / downSampleRatio;

    // down sampling
    float[] lowres = new float[reducedWidth * reducedHeight * 3];
    downSample(width, height, input, reducedWidth, reducedHeight, lowres);

    // select bright pixel
    float[] selected = new float[reducedWidth * reducedHeight * 3];
    selectBrightPixels(reducedWidth, reducedHeight, lowres, threshold, selected);

    // blur horizontally
    float[] horizontallyBlurred = lowres; // reusing the buffer but with different name for readability
    lowres = null;
    horizontalBlur(reducedWidth, reducedHeight, selected, blurRadius, blurKernel, horizontallyBlurred);

    // blur vertically
    float[] blurred = selected; // reused buffer
    selected = null;
    verticalBlur(reducedWidth, reducedHeight, blurRadius, blurKernel, horizontallyBlurred, blurred);


    double[] pixelBuffer = new double[3];
    for(int y = 0; y < height; ++y) {
      for(int x = 0; x < width; ++x) {
        int pixelIndex = (y * width + x) * 3;
        interpolatePixel(reducedWidth, reducedHeight, blurred, x, y, pixelBuffer);
        for(int i = 0; i < 3; ++i) {
          if(!highlightOnly)
            pixelBuffer[i] += input[pixelIndex+i];
          pixelBuffer[i] *= exposure;
          pixelBuffer[i] = FastMath.pow(pixelBuffer[i], 1 / Scene.DEFAULT_GAMMA);
          pixelBuffer[i] = Math.min(1.0, pixelBuffer[i]);
        }
        output.setPixel(x, y, ColorUtil.getRGB(pixelBuffer));
      }
    }
  }

  private void interpolatePixel(int reducedWidth, int reducedHeight, float[] blurred, int x, int y, double[] pixelBuffer) {
    double smallx = x / (double)downSampleRatio;
    double smally = y / (double)downSampleRatio;
    int x0 = Math.max(0, (int)(smallx));
    int x1 = Math.min(reducedWidth-1, (int)(smallx + 1));
    int y0 = Math.max(0, (int)(smally));
    int y1 = Math.min(reducedHeight-1, (int)(smally + 1));
    double tx = smallx - (int)smallx;
    double ty = smally - (int)smally;

    for(int i = 0; i < 3; ++i) {
      double hor0 = blurred[(y0 * reducedWidth + x0) * 3 + i] * (1 - tx) + blurred[(y0 * reducedWidth + x1) * 3 + i] * tx;
      double hor1 = blurred[(y1 * reducedWidth + x0) * 3 + i] * (1 - tx) + blurred[(y1 * reducedWidth + x1) * 3 + i] * tx;
      double value = hor0 * (1 - ty) + hor1 * ty;
      pixelBuffer[i] = value;
    }
  }

  private void interpolatePixelClosest(int reducedWidth, int reducedHeight, float[] blurred, int x, int y, double[] pixelBuffer) {
    int smallx = x / downSampleRatio;
    int smally = y / downSampleRatio;

    for(int i = 0; i < 3; ++i) {
      pixelBuffer[i] = blurred[(smally * reducedWidth + smallx) * 3 + i];
    }
  }

  private void verticalBlur(int reducedWidth, int reducedHeight, int blurRadius, float[] blurKernel, float[] horizontallyBlurred, float[] blurred) {
    for(int y = 0; y < reducedHeight; ++y) {
      for(int x = 0; x < reducedWidth; ++x) {
        int pixelIndex = (y * reducedWidth + x) * 3;
        float kernelSum = 0.0f;
        for(int i = 0; i < 3; ++i) {
          blurred[pixelIndex + i] = 0;
        }

        for(int kernelIndex = -blurRadius; kernelIndex <= blurRadius; ++kernelIndex) {
          int currentY = y + kernelIndex;
          if(currentY < 0 || currentY >= reducedHeight)
            continue;

          kernelSum += blurKernel[blurRadius + kernelIndex];

          int currentPixelIndex = (currentY * reducedWidth + x) * 3;
          for(int i = 0; i < 3; ++i) {
            blurred[pixelIndex + i] += blurKernel[blurRadius + kernelIndex] * horizontallyBlurred[currentPixelIndex + i];
          }
        }

        for(int i = 0; i < 3; ++i) {
          blurred[pixelIndex + i] /= kernelSum;
        }
      }
    }
  }

  private void horizontalBlur(int reducedWidth, int reducedHeight, float[] selected, int blurRadius, float[] blurKernel, float[] horizontallyBlurred) {
    for(int y = 0; y < reducedHeight; ++y) {
      for(int x = 0; x < reducedWidth; ++x) {
        int pixelIndex = (y * reducedWidth + x) * 3;
        float kernelSum = 0.0f;
        for(int i = 0; i < 3; ++i) {
          horizontallyBlurred[pixelIndex + i] = 0;
        }

        for(int kernelIndex = -blurRadius; kernelIndex <= blurRadius; ++kernelIndex) {
          int currentX = x + kernelIndex;
          if(currentX < 0 || currentX >= reducedWidth)
            continue;

          kernelSum += blurKernel[blurRadius + kernelIndex];

          int currentPixelIndex = (y * reducedWidth + currentX) * 3;
          for(int i = 0; i < 3; ++i) {
            horizontallyBlurred[pixelIndex + i] += blurKernel[blurRadius + kernelIndex] * selected[currentPixelIndex + i];
          }
        }

        for(int i = 0; i < 3; ++i) {
          horizontallyBlurred[pixelIndex + i] /= kernelSum;
        }
      }
    }
  }

  private void selectBrightPixels(int reducedWidth, int reducedHeight, float[] lowres, double threshold, float[] selected) {
    for(int y = 0; y < reducedHeight; ++y) {
      for(int x = 0; x < reducedWidth; ++x) {
        int pixelIndex = (y * reducedWidth + x) * 3;
        float brightness = lowres[pixelIndex] * 0.2126f
                         + lowres[pixelIndex] * 0.7152f
                         + lowres[pixelIndex] * 0.0722f;
        if(brightness > threshold) {
          System.arraycopy(lowres, pixelIndex, selected, pixelIndex, 3);
        }
      }
    }
  }

  private void downSample(int width, int height, double[] input, int reducedWidth, int reducedHeight, float[] lowres) {
    for(int y = 0; y < reducedHeight; ++y) {
      for(int x = 0; x < reducedWidth; ++x) {
        int lowresPixelIndex = (y * reducedWidth + x) * 3;
        int count = 0;
        for(int realy = y * downSampleRatio; realy < y * downSampleRatio + downSampleRatio && realy < height; ++realy) {
          for(int realx = x * downSampleRatio; realx < x * downSampleRatio + downSampleRatio && realx < width; ++realx) {
            int highresPixelIndex = (realy * width + realx) * 3;
            for(int i = 0; i < 3; ++i) {
              lowres[lowresPixelIndex + i] += input[highresPixelIndex+i];
            }
            ++count;
          }
        }
        for(int i = 0; i < 3; ++i) {
          lowres[lowresPixelIndex + i] /= count;
        }
      }
    }
  }

  private float[] computeBlurKernel(int blurRadius) {
    float[] kernel = new float[2*blurRadius + 1];
    float sigma = blurRadius / 3.0f;
    float factor = 1 / (2 * sigma * sigma);

    float sum = 0.0f;
    for(int i = -blurRadius; i <= blurRadius; ++i) {
      kernel[i+blurRadius] = (float)Math.exp(- (i*i) * factor);
      sum += kernel[i+blurRadius];
    }
    for(int i = 0; i <= blurRadius*2; ++i) {
      kernel[i] /= sum;
    }

    return kernel;
  }

  @Override
  public String getName() {
    return "Bloom";
  }

  @Override
  public String getId() {
    return "BLOOM";
  }
}
