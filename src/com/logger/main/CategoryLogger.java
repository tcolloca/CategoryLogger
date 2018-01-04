package com.logger.main;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryLogger {

	private String defaultCategory;
	private String categoryFormat;

	private final Map<Object, LogTimeInfo> timeMap = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, Boolean> categoriesMap = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, String> categoryParents = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, List<String>> categoryOuts = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, List<LogListener>> categoryListeners = Collections.synchronizedMap(new HashMap<>());
	private final PrintStream out;
	
	public CategoryLogger(PrintStream out) {
		if (out == null) {
			throw new IllegalArgumentException("out is null.");
		}
		this.out = out;
	}
	
	/**
	 * Adds a listener for the category.
	 */
	public void addListener(String category, LogListener listener) {
		categoryListeners.putIfAbsent(category, new ArrayList<>());
		categoryListeners.get(category).add(listener);
	}
	
	/**
	 * Adds a a file given by it's path as a log for the category.
	 */
	public void addFileLog(String category, String outFile) {
		categoryOuts.putIfAbsent(category, new ArrayList<>());
		categoryOuts.get(category).add(outFile);
	}
	
	/**
	 * Adds the list of categories as children of the first one.
	 */
	public void addCategoryChildren(String category, List<String> children) {
		if (category == null) {
			throw new IllegalArgumentException("category is null.");
		}
		if (children == null) {
			throw new IllegalArgumentException("children is null.");
		}
		if (children.isEmpty()) {
			throw new IllegalArgumentException("children is empty.");
		}
		for (String child : children) {
			categoryParents.put(child, category);
		}
	}
	
	/**
	 * Enables the categories.
	 */
	public void enableCategories(String ... categories) {
		updateCategories(true, categories);
	}
	
	/**
	 * Disables the categories.
	 */
	public void disableCategories(String ... categories) {
		updateCategories(false, categories);
	}
	
	private void updateCategories(boolean value, String ... categories) {
		if (categories == null) {
			throw new IllegalArgumentException("categories is null.");
		}
		if (Arrays.asList(categories).contains(null)) {
			throw new IllegalArgumentException("A category is null.");
		}
		Arrays.stream(categories).forEach(cat -> categoriesMap.put(cat, value));
	}
	
	/**
	 * Sets the default category.
	 */
	public CategoryLogger setDefaultCategory(String defaultCategory) {
		if (defaultCategory == null) {
			throw new IllegalArgumentException("defaultCategory is null.");
		}
		this.defaultCategory = defaultCategory;
		return this;
	}
	
	/**
	 * Sets the format for when displaying the category. If null, it won't be displayed.
	 */
	public CategoryLogger setCategoryFormat(String format) {
		if (format == null) {
			throw new IllegalArgumentException("format is null.");
		}
		this.categoryFormat = format;
		return this;
	}
	
	/**
	 * Logs the message if the default category is enabled.
	 */
	public void log(Object message) {
		log(null, message);
	}
	
	/**
	 * Logs the message if the category is enabled.
	 */
	public void log(String category, Object message) {
		if (message == null) {
			throw new IllegalArgumentException("message is null.");
		}
		log(category, new Object[] {message});
	}
	
	/**
	 * Logs the message if the category is enabled.
	 */
	public void log(String category, Object ... messageAndParams) {
		if (messageAndParams == null || messageAndParams.length == 0) {
			throw new IllegalArgumentException("messageAndParams is null or empty.");
		}
		String message = messageAndParams[0].toString();
		Object[] params = Arrays.copyOfRange(messageAndParams, 1, messageAndParams.length);
		if (messageAndParams[0] == null) {
			throw new IllegalArgumentException("message is null.");
		}
		String usingCategory = category == null ? defaultCategory : category;
		List<String> closure = closureParents(usingCategory);
		for (String parent : closure) {
			if (categoriesMap.containsKey(parent) && !categoriesMap.get(parent)) {
				return;
			}
		}
		String categoryLog = categoryFormat != null && usingCategory != null ? 
				String.format(categoryFormat, usingCategory) : "";
		
		String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		System.out.println(message);
		System.out.println(Arrays.toString(params));
		String logMessage = now + ": " + categoryLog + String.format(message, params) + "\r\n";
		synchronized(this) {	
			out.append(logMessage);
			for (String parent : closure) {
				if (categoryOuts.containsKey(parent)) {
					for (String outFilePath : categoryOuts.get(parent)) {
						try {
							PrintStream fileOut = new PrintStream(
									new FileOutputStream(Paths.get(outFilePath).toFile(), true));
							fileOut.append(logMessage);
							fileOut.close();
						} catch (FileNotFoundException e) {
							throw new IllegalStateException("Couldn't find log file.", e);
						}
					}
				}
				if (categoryListeners.containsKey(parent)) {
					for (LogListener listener : categoryListeners.get(parent)) {
						listener.onLog(usingCategory, logMessage);
					}
				}
			}
		}
	}
	
	/**
	 * Logs start time with the corresponding key and time units.
	 */
	public void logTimeStart(Object key, TimeUnits timeUnits) {
		if (key == null) {
			throw new IllegalArgumentException("key is null.");
		}
		if (timeUnits == null) {
			throw new IllegalArgumentException("timeUnits is null.");
		}
		timeMap.put(key, new LogTimeInfo(timeUnits, System.nanoTime()));
	}
	
	/**
	 * Logs end time for the corresponding key with the corresponding message. It should contain %f.
	 */
	public void logTimeEnd(Object key, String message) {
		logTimeEnd(key, null, message);
	}
	
	/**
	 * Logs end time for the corresponding key with the corresponding message, under the category. It should contain %f.
	 */
	public void logTimeEnd(Object key, String category, String message) {
		if (key == null) {
			throw new IllegalArgumentException("key is null.");
		}
		if (message == null) {
			throw new IllegalArgumentException("message is null.");
		}
		if (!timeMap.containsKey(key)) {
			throw new IllegalArgumentException("Never started logging time with key: " + key);
		}
		LogTimeInfo logTimeInfo = timeMap.get(key);
		long duration = System.nanoTime() - logTimeInfo.getStart();
		logTime(category, message, duration, TimeUnits.NANOSECONDS, logTimeInfo.getUnits());
	}

	/**
	 * Logs time with the corresponding message, under the category. It should contain %f.
	 */
	public void logTime(String category, String message, long time, TimeUnits srcUnits, TimeUnits dstUnits) {
		logTime(category, message, (double) time, srcUnits, dstUnits);
	}
	
	/**
	 * Logs time with the corresponding message, under the category. It should contain %f.
	 */
	public void logTime(String category, String message, double time, TimeUnits srcUnits, TimeUnits dstUnits) {
		if (message == null) {
			throw new IllegalArgumentException("message is null.");
		}
		if (srcUnits == null) {
			throw new IllegalArgumentException("srcUnits is null.");
		}
		if (dstUnits == null) {
			throw new IllegalArgumentException("dstUnits is null.");
		}
		log(category, String.format(message, getTime(time, srcUnits, dstUnits)));
	}
	
	private double getTime(double time, TimeUnits src, TimeUnits dst) {
		if (src.equals(dst)) {
			return time;
		}
		double nanoTime = time * nanoPow(src);
		return nanoTime / nanoPow(dst);
	}
	
	private List<String> closureParents(String category) {
		List<String> parents = new ArrayList<>();
		if (categoryParents.containsKey(category)) {
			parents.addAll(closureParents(categoryParents.get(category)));
		}
		parents.add(category);
		return parents;
	}
	
	private long nanoPow(TimeUnits units) {
		switch (units) {
		case NANOSECONDS:
			return 1;
		case MILLISECONDS:
			return (long) 1e6;
		case SECONDS:
			return (long) 1e9;
		case MINUTES:
			return (long) 1e9 * 60;
		case HOURS:
			return (long) 1e9 * 3600; 
		default:
			throw new IllegalStateException("Time unit " + units + " not supported.");
		}
	}
}
