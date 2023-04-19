/*
 * Copyright 2013 Luciad (http://www.luciad.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.luciad.imageio.webp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Iterator;

final class WebP {
  private static boolean NATIVE_LIBRARY_LOADED = false;

  static synchronized void loadNativeLibrary() {
    if (!NATIVE_LIBRARY_LOADED) {
      //System.loadLibrary("webp-imageio");
      NativeLibraryUtils.loadFromJar();
      NATIVE_LIBRARY_LOADED = true;
    }
  }

  // TODO: lazy load native library
  static {
    loadNativeLibrary();
  }
	public static void main(String[] args) {
		System.out.println("Hello");
	}
  private WebP() {
  }

  public static int[] decode(WebPDecoderOptions aOptions, byte[] aData, int aOffset, int aLength, int[] aOut) throws IOException {
    if (aOptions == null) {
      throw new NullPointerException("Decoder options may not be null");
    }

    if (aData == null) {
      throw new NullPointerException("Input data may not be null");
    }

    if (aOffset + aLength > aData.length) {
      throw new IllegalArgumentException("Offset/length exceeds array size");
    }

    int[] pixels = decode(aOptions.fPointer, aData, aOffset, aLength, aOut, ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN));
    VP8StatusCode status = VP8StatusCode.getStatusCode(aOut[0]);
    switch (status) {
      case VP8_STATUS_OK:
        break;
      case VP8_STATUS_OUT_OF_MEMORY:
        throw new OutOfMemoryError();
      default:
        throw new IOException("Decode returned code " + status);
    }

    return pixels;
  }

  private static native int[] decode(long aDecoderOptionsPointer, byte[] aData, int aOffset, int aLength, int[] aFlags, boolean aBigEndian);

  public static int[] getInfo(byte[] aData, int aOffset, int aLength) throws IOException {
    int[] out = new int[2];
    int result = getInfo(aData, aOffset, aLength, out);
    if (result == 0) {
      throw new IOException("Invalid WebP data");
    }

    return out;
  }

  private static native int getInfo(byte[] aData, int aOffset, int aLength, int[] aOut);

  public static byte[] encodeRGBA(WebPEncoderOptions aOptions, byte[] aRgbaData, int aWidth, int aHeight, int aStride) {
    return encodeRGBA(aOptions.fPointer, aRgbaData, aWidth, aHeight, aStride);
  }

  private static native byte[] encodeRGBA(long aConfig, byte[] aRgbaData, int aWidth, int aHeight, int aStride);

  public static byte[] encodeRGB(WebPEncoderOptions aOptions, byte[] aRgbaData, int aWidth, int aHeight, int aStride) {
    return encodeRGB(aOptions.fPointer, aRgbaData, aWidth, aHeight, aStride);
  }

  private static native byte[] encodeRGB(long aConfig, byte[] aRgbaData, int aWidth, int aHeight, int aStride);

}


enum Arch{
	Linux64 {
		String os() {
			return "linux";
		}

		String libFile() {
			return "libwebp-imageio.so";
		}

	},Win32 {

		String os() {
			return "win";
		}
		
		String bits() {
			return "32";
		}
		
		String libFile() {
			return "webp-imageio.dll";
		}
	},Win64 {
		String os() {
			return "win";
		}

		String libFile() {
			return "webp-imageio.dll";
		}
		
	},Mac64 {

		String os() {
			return "mac";
		}

		String libFile() {
			return "libwebp-imageio.dylib";
		}
	},Macaarch64 {

		String os() {
			return "mac";
		}

		String arch() {
			return "arm";
		}
		
		String libFile() {
			return "libwebp-imageio.dylib";
		}
		
	};
	
	abstract String os();
	abstract String libFile();
	
	String libraryPath() {
		return String.format("/native/%s/%s/%s/%s", os(), arch(), bits(), libFile());
	}
	
	String bits() {
		return "64";
	}
	
	String arch() {
		return "intel";
	}
	
	static Arch detect() {
		String os = System.getProperty("os.name").toLowerCase();
		String bits = System.getProperty("os.arch").contains("64") ? "64": "32";
		String arch = System.getProperty("os.arch").toLowerCase().contains("aarch64")?"arm":"intel";
		for (Arch a:values()) {
			if (os.contains(a.os())&&bits.equals(a.bits())&&arch.equals(a.arch()))
					return a;
		}
		return Linux64;
	}
	
}


class NativeLibraryUtils {

  public static void loadFromJar() {
   
    // copy the native lib from the jar to a temp file on disk and load from there
    final Arch architecture = Arch.detect();
	try (InputStream in = NativeLibraryUtils.class.getResourceAsStream(architecture.libraryPath())){
      if(in == null) {
        throw new RuntimeException(String.format("Could not find WebP native library for %s %s in the jar", architecture.os(), architecture.bits()));
      }

      File tmpLibraryFile = Files.createTempFile("", architecture.libFile()).toFile();
      tmpLibraryFile.deleteOnExit();
      try (FileOutputStream out = new FileOutputStream(tmpLibraryFile)) {
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          out.write(buffer, 0, bytesRead);
        }
      }

      System.load(tmpLibraryFile.getAbsolutePath());

    } catch (IOException e) {
      throw new RuntimeException("Could not load native WebP library", e);
    }
  }
}
