/**
 * This file is part of VeraPDF Library GUI, a module of the veraPDF project.
 * Copyright (c) 2015, veraPDF Consortium <info@verapdf.org> All rights
 * reserved. VeraPDF Library GUI is free software: you can redistribute it
 * and/or modify it under the terms of either: The GNU General public license
 * GPLv3+. You should have received a copy of the GNU General Public License
 * along with VeraPDF Library GUI as the LICENSE.GPL file in the root of the
 * source tree. If not, see http://www.gnu.org/licenses/ or
 * https://www.gnu.org/licenses/gpl-3.0.en.html. The Mozilla Public License
 * MPLv2+. You should have received a copy of the Mozilla Public License along
 * with VeraPDF Library GUI as the LICENSE.MPL file in the root of the source
 * tree. If a copy of the MPL was not distributed with this file, you can obtain
 * one at http://mozilla.org/MPL/2.0/.
 */
/**
 *
 */
package org.verapdf.cli;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.verapdf.ReleaseDetails;
import org.verapdf.apps.Applications;
import org.verapdf.apps.ConfigManager;
import org.verapdf.apps.SoftwareUpdater;
import org.verapdf.cli.commands.VeraCliArgParser;
import org.verapdf.core.VeraPDFException;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.validation.profiles.ProfileDirectory;
import org.verapdf.pdfa.validation.profiles.Profiles;
import org.verapdf.pdfa.validation.profiles.ValidationProfile;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * @author <a href="mailto:carl@openpreservation.org">Carl Wilson</a>
 */
public final class VeraPdfCli {
	private static final Logger logger = Logger.getLogger(VeraCliArgParser.class.getCanonicalName());
	private static final ConfigManager configManager = Applications.createAppConfigManager();
	private static final int MEGABYTE = (1024 * 1024);
	private static final String FLAVOURS_HEADING = CliConstants.APP_NAME + " supported PDF/A profiles:"; //$NON-NLS-1$
	private static final ProfileDirectory PROFILES = Profiles.getVeraProfileDirectory();
	private static final String EXIT = "q";

	private VeraPdfCli() {
		// disable default constructor
	}

	/**
	 * Main CLI entry point, process the command line arguments
	 *
	 * @param args Java.lang.String array of command line args, to be processed
	 *             using Apache commons CLI.
	 */
	public static void main(final String[] args) throws VeraPDFException {
		MemoryMXBean memoryMan = ManagementFactory.getMemoryMXBean();
		ReleaseDetails.addDetailsFromResource(
				ReleaseDetails.APPLICATION_PROPERTIES_ROOT + "app." + ReleaseDetails.PROPERTIES_EXT); //$NON-NLS-1$
		VeraCliArgParser cliArgParser = new VeraCliArgParser();
		JCommander jCommander = new JCommander(cliArgParser);
		jCommander.setProgramName(CliConstants.APP_NAME);

		try {
			jCommander.parse(args);
		} catch (ParameterException e) {
			System.err.println(e.getMessage());
			displayHelpAndExit(cliArgParser, jCommander, 1);
		}
		if (cliArgParser.isHelp()) {
			displayHelpAndExit(cliArgParser, jCommander, 0);
		}
		messagesFromParser(cliArgParser);
		if (isProcess(cliArgParser)) {
			try (VeraPdfCliProcessor processor = VeraPdfCliProcessor.createProcessorFromArgs(cliArgParser,
					configManager)) {
				if (args.length == 0)
					jCommander.usage();
				// FIXME: trap policy IO Exception (deliberately left un-caught
				// for development)

				//todo: server mode
				processor.processPaths(cliArgParser.getPdfPaths());
				File tempFile = processor.getTempFile();
				if (tempFile != null) {
					System.out.println(tempFile.getAbsoluteFile());
				}

				if (cliArgParser.isServerMode()) {
					Scanner scanner = new Scanner(System.in);
					while (scanner.hasNextLine()) {
						String path = scanner.nextLine();
						if (path != null) {
							if (path.equals(EXIT)) {
								break;
							} else {
								List<String> pathes = new ArrayList<>();
								pathes.add(path);
								cliArgParser.setPdfPaths(pathes);
								processor.processPaths(cliArgParser.getPdfPaths());

								System.out.println(processor.getTempFile().getAbsolutePath());
							}
						}
					}
				}
			} catch (OutOfMemoryError oome) {
				final String message = "The JVM appears to have run out of memory"; //$NON-NLS-1$
				logger.log(Level.WARNING, message, oome);
				MemoryUsage heapUsage = memoryMan.getHeapMemoryUsage();
				long maxMemory = heapUsage.getMax() / MEGABYTE;
				long usedMemory = heapUsage.getUsed() / MEGABYTE;
				System.out.format(",%s\n", message); //$NON-NLS-1$
				System.out.format("Memory Use: %sM/%sM\n", Long.valueOf(usedMemory), Long.valueOf(maxMemory)); //$NON-NLS-1$
				System.out.format(
						"To increase the memory available to the JVM please assign the JAVA_OPTS environment variable.\n"); //$NON-NLS-1$
				System.out.format("The examples below increase the maximum heap available to the JVM to 2GB:\n"); //$NON-NLS-1$
				System.out.format(" - Mac or Linux users:\n"); //$NON-NLS-1$
				System.out.format("   export JAVA_OPTS=\"-Xmx2048m\"\n"); //$NON-NLS-1$
				System.out.format(" - Windows users:\n"); //$NON-NLS-1$
				System.out.format("   SET JAVA_OPTS=\"-Xmx2048m\"\n"); //$NON-NLS-1$
				System.exit(1);
			}
		}
	}

	public static void displayHelpAndExit(VeraCliArgParser cliArgParser, JCommander jCommander, int i) {
		showVersionInfo(cliArgParser.isVerbose());
		jCommander.usage();
		System.exit(i);
	}

	private static void messagesFromParser(final VeraCliArgParser parser) {

		if (parser.listProfiles()) {
			listProfiles();
		}

		if (parser.showVersion()) {
			showVersionInfo(parser.isVerbose());
		}
	}

	private static void listProfiles() {
		System.out.println(FLAVOURS_HEADING);
		EnumSet<PDFAFlavour> flavs = EnumSet.copyOf(PROFILES.getPDFAFlavours());
		for (PDFAFlavour flav : flavs) {
			ValidationProfile profile = PROFILES.getValidationProfileByFlavour(flav);
			System.out.format("  %s - %s", profile.getPDFAFlavour().getId(), profile.getDetails().getName());//$NON-NLS-1$
			System.out.println();
		}
	}

	private static void showVersionInfo(final boolean isVerbose) {
		ReleaseDetails appDetails = Applications.getAppDetails();
		System.out.format("%s %s\n", CliConstants.APP_NAME, appDetails.getVersion()); //$NON-NLS-1$
		System.out.format("Built: %s\n", appDetails.getBuildDate()); //$NON-NLS-1$
		System.out.format("%s\n", ReleaseDetails.rightsStatement()); //$NON-NLS-1$
		if (isVerbose)
			showUpdateInfo(appDetails);
	}

	private static void showUpdateInfo(final ReleaseDetails details) {
		SoftwareUpdater updater = Applications.softwareUpdater();
		if (!updater.isOnline()) {
			logger.log(Level.WARNING, Applications.UPDATE_SERVICE_NOT_AVAILABLE); //$NON-NLS-1$
			return;
		}
		if (!updater.isUpdateAvailable(details)) {
			System.out.format(Applications.UPDATE_LATEST_VERSION, ",", details.getVersion() + "\n"); //$NON-NLS-1$
			return;
		}
		System.out.format(
				Applications.UPDATE_OLD_VERSION, //$NON-NLS-1$
				details.getVersion(), updater.getLatestVersion(details));
		System.out.format("You can download the latest version from: %s.\n", //$NON-NLS-1$
				Applications.UPDATE_URI); //$NON-NLS-1$
	}

	private static boolean isProcess(final VeraCliArgParser parser) {
		if (parser.getPdfPaths().isEmpty() && (parser.isHelp() || parser.listProfiles() || parser.showVersion())) {
			return false;
		}
		return true;
	}
}
