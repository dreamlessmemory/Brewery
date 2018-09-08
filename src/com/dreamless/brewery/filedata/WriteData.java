package com.dreamless.brewery.filedata;


import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.file.FileConfiguration;

import com.dreamless.brewery.P;

public class WriteData implements Runnable {

	private FileConfiguration data;

	public WriteData(FileConfiguration data) {
		this.data = data;
	}

	@Override
	public void run() {
		File datafile = new File(P.p.getDataFolder(), "data.yml");

		try {
			data.save(datafile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		DataSave.lastSave = 1;
		DataSave.running = null;
	}
}
