/*
 * Copyright � 2014 - 2017 | Wurst-Imperium | All rights reserved.
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.wurstclient.updater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.UIManager;

public final class WurstUpdater
{
	private final ProgressDialog progress = new ProgressDialog();
	
	public static void main(String[] args)
	{
		if(args.length != 4 || !"update".equals(args[0]))
		{
			System.out.println(
				"Syntax: update <path> <wurst_version> <minecraft_version>");
			return;
		}
		
		try
		{
			new WurstUpdater().update(Paths.get(args[1]), args[2], args[3]);
			
		}catch(Throwable e)
		{
			e.printStackTrace();
		}
	}
	
	public void update(Path dir, String wurstVersion, String mcVersion)
		throws IOException
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			
		}catch(Exception e)
		{
			throw new RuntimeException(e);
		}
		
		try
		{
			progress.setVisible(true);
			
			progress.setLine1("Preparing Download");
			URL url = URI.create(
				"https://github.com/Wurst-Imperium/Wurst-MCX/releases/download/v"
					+ wurstVersion + "/Wurst-Client-v" + wurstVersion + "-MC"
					+ mcVersion + ".zip")
				.toURL();
			Path tmp = Files.createTempFile(dir, null, null);
			
			try
			{
				progress.setLine1("Downloading Update");
				download(url, tmp);
				
				progress.setLine1("Extracting Update");
				extract(tmp, dir);
				
			}finally
			{
				Files.deleteIfExists(tmp);
			}
			
			progress.setLine1("Update Ready");
			progress.setLine2("Restart Minecraft to install it.");
			finishInstallation(dir);
			
			System.out.println("Done.");
			
		}finally
		{
			progress.dispose();
		}
	}
	
	private void download(URL url, Path temporaryZip) throws IOException
	{
		URLConnection connection = url.openConnection();
		
		double bytesTotal = connection.getContentLength();
		if(bytesTotal <= 0)
			throw new IOException("Invalid content length: " + bytesTotal);
		double bytesDownloaded = 0;
		
		NumberFormat percentFormat =
			NumberFormat.getPercentInstance(Locale.ENGLISH);
		percentFormat.setMinimumFractionDigits(1);
		NumberFormat dataFormat =
			NumberFormat.getNumberInstance(Locale.ENGLISH);
		dataFormat.setMinimumFractionDigits(3);
		
		String dataSuffix =
			" / " + dataFormat.format(bytesTotal / 1048576D) + " MB";
		
		try(InputStream input = connection.getInputStream();
			OutputStream output = Files.newOutputStream(temporaryZip))
		{
			byte[] buffer = new byte[8192];
			for(int length; (length = input.read(buffer)) != -1; output
				.write(buffer, 0, length))
			{
				bytesDownloaded += length;
				
				progress
					.setLine2(percentFormat.format(bytesDownloaded / bytesTotal)
						+ " (" + dataFormat.format(bytesDownloaded / 1048576D)
						+ dataSuffix + ")");
			}
		}
		
		progress.setLine2(null);
	}
	
	private void extract(Path zip, Path dir) throws IOException
	{
		try(ZipInputStream input =
			new ZipInputStream(Files.newInputStream(zip)))
		{
			for(ZipEntry entry; (entry = input.getNextEntry()) != null;)
			{
				String name = entry.getName();
				String ext = name.substring(name.lastIndexOf("."));
				
				if(!ext.equals(".jar") && !ext.equals(".json"))
					continue;
				
				Files.copy(input,
					dir.resolve(dir.getFileName() + ext + ".update"),
					StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
	
	private void finishInstallation(Path dir) throws IOException
	{
		String releaseName = dir.getFileName().toString();
		
		delete(dir.resolve(releaseName + ".jar"));
		delete(dir.resolve(releaseName + ".json"));
		
		System.out.println("Renaming .update files...");
		try(DirectoryStream<Path> stream =
			Files.newDirectoryStream(dir, "*.update"))
		{
			for(Path path : stream)
			{
				String name = path.getFileName().toString();
				name = name.substring(0, name.length() - 7);
				Path target = path.resolveSibling(name);
				Files.move(path, target);
			}
		}
	}
	
	private void delete(Path path)
	{
		System.out.println("Deleting " + path.getFileName());
		while(Files.exists(path))
			try
			{
				Files.deleteIfExists(path);
			}catch(IOException e)
			{
				
			}
	}
}
