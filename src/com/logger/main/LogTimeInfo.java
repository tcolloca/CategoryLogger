package com.logger.main;

class LogTimeInfo {

	private final TimeUnits units;
	private final long start;

	LogTimeInfo(TimeUnits units, long start) {
		super();
		this.units = units;
		this.start = start;
	}

	TimeUnits getUnits() {
		return units;
	}

	long getStart() {
		return start;
	}
}
