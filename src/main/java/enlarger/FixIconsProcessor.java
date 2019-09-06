package enlarger;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.imgscalr.Scalr;

import com.mortennobel.imagescaling.AdvancedResizeOp;
import com.mortennobel.imagescaling.ResampleOp;

public class FixIconsProcessor
{
	private static final Logger logger = Logger.getGlobal();
	
	public void processDirectory(File directory, File outputDirectory, float resizeFactor)
			throws Exception {
		logger.info("Processing directory [" + directory.getAbsolutePath()
				+ "]");

		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				File targetDir = new File(outputDirectory.getAbsolutePath()
						+ File.separator + file.getName());
				logger.info("Creating directory: "
						+ targetDir.getAbsolutePath());
				targetDir.mkdir();
				processDirectory(file, targetDir, resizeFactor);
			} else {
				File targetFile = new File(outputDirectory.getAbsolutePath()
						+ File.separator + file.getName());

				if (file.getName().toLowerCase().endsWith(".zip")
						|| file.getName().toLowerCase().endsWith(".jar")) {
					logger.info("Processing archive file: "
							+ file.getAbsolutePath());
					ZipFile zipSrc = null;
					ZipOutputStream outStream = null;
					try
					{
						zipSrc = new ZipFile(file);
						Enumeration<? extends ZipEntry> srcEntries = zipSrc.entries();

						outStream = new ZipOutputStream(
								new FileOutputStream(targetFile));

						while (srcEntries.hasMoreElements()) {
							ZipEntry entry = (ZipEntry) srcEntries.nextElement();
							logger.info("Processing zip entry [" + entry.getName()
									+ "]");

							ZipEntry newEntry = new ZipEntry(entry.getName());
							try {
								outStream.putNextEntry(newEntry);
							} catch (Exception e) {
								if (!e.getMessage().startsWith("duplicate entry: ")) {
									logger.log(Level.SEVERE, "Error: ", e);
								} else {
									logger.log(Level.SEVERE, e.getMessage(), e);
								}
								outStream.closeEntry();
								continue;
							}

							BufferedInputStream bis = new BufferedInputStream(
									zipSrc.getInputStream(entry));

							if (ImageType.findType(entry.getName()) != null) {
								processImage(zipSrc.getName() + "!/" + entry.getName(), bis, outStream, resizeFactor);
							} else {
								IOUtils.copy(bis, outStream);
							}

							outStream.closeEntry();
							bis.close();
						}

					} catch (Exception e)
					{
						logger.log(Level.SEVERE, "Can't process file: "+file.getAbsolutePath(), e);
					}
					finally
					{
						try
						{
							if(zipSrc!=null) zipSrc.close();
							if(outStream!=null) outStream.close();
						} catch (IOException e)
						{
							logger.log(Level.SEVERE, "Can't close zip streams for file: "+file.getAbsolutePath(), e);
						}
					}
				} else if (ImageType.findType(file.getName()) != null) {
					logger.info("Processing image: " + file.getAbsolutePath());

					FileInputStream inStream = null;
					FileOutputStream outStream = null;

					try {
						inStream = new FileInputStream(file);
						outStream = new FileOutputStream(targetFile);
						processImage(file.getName(), inStream, outStream, resizeFactor);
					} finally {
						IOUtils.closeQuietly(inStream);
						IOUtils.closeQuietly(outStream);
					}
				} else {
					logger.info("Processing : " + file.getAbsolutePath());

					FileInputStream inStream = null;
					FileOutputStream outStream = null;

					try {
						inStream = new FileInputStream(file);
						outStream = new FileOutputStream(targetFile);
						IOUtils.copy(inStream, outStream);
					} finally {
						IOUtils.closeQuietly(inStream);
						IOUtils.closeQuietly(outStream);
					}

				}

			}

		}

	}

	public void processImage(String fileName, InputStream input,
			OutputStream output, float resizeFactor) throws IOException {

		logger.info("Scaling image: " + fileName);

		boolean imageWriteStarted = false;
		try {
			BufferedImage out = ImageIO.read(input);

			int outWidth = (int) (out.getWidth() * resizeFactor);
			int outHeight = (int) (out.getHeight() * resizeFactor);

			BufferedImage rescaledOut = createResizedCopy(out, outWidth, outHeight);

			ImageIO.write(rescaledOut, ImageType.findType(fileName).name(),
					output);

		} catch (Exception e) {
			if (imageWriteStarted) {
				throw new RuntimeException("Failed to scale image [" + fileName
						+ "]: " + e.getMessage(), e);
			} else {
				logger.log(Level.SEVERE,
						"Unable to scale [" + fileName + "]: " + e.getMessage(),
						e);
				IOUtils.copy(input, output);
			}
		}
	}

	private BufferedImage createResizedCopy(BufferedImage originalImage, int scaledWidth, int scaledHeight) {
		
		try {
			ResampleOp resampleOp = new ResampleOp(scaledWidth,scaledHeight);
			resampleOp.setUnsharpenMask(AdvancedResizeOp.UnsharpenMask.Normal);
			BufferedImage scaledBI = resampleOp.filter(originalImage, null);

			return scaledBI;
		} catch (RuntimeException e) {
			
			// Resample failed - maybe the image was too small, try another way (Scalr)
			BufferedImage scaledBI = Scalr.resize(originalImage, Scalr.Method.ULTRA_QUALITY, scaledWidth, scaledHeight);
			return scaledBI;
		}
	}
}
