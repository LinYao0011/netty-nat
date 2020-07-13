/** 
 * Project Name: farm-core 
 * File Name: FileWatchService.java 
 * Package Name: core.frame.filewatch
 * Date: 2017年3月18日下午4:21:15 
 * Copyright (c) 2017, hadlinks All Rights Reserved. 
 * 
 */
package core.frame.filewatch;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import org.apache.logging.log4j.*;
import core.frame.loader.AbstractLoader;

/** 
 * ClassName: FileWatchService 
 * Function: 文件监听服务，可以对某个目录下的文件进行监听，发生修改事件后触发任务.  
 * date: 2017年3月18日 下午4:21:15  
 * 
 * @author songwei (songw@hadlinks.com)
 * @version 
 * @since JDK 1.8 
 */
public class FileWatchService {
	
	private final Logger logger = LogManager.getLogger(FileWatchService.class);
	private WatchService watchService = null;
	
	public void addWatcher(String pathStr, AbstractLoader loader){
		Path path = Paths.get(pathStr);
		try{
			watchService = FileSystems.getDefault().newWatchService();
			path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
			WatchKey key = null;
			while(true){
				key = watchService.take();
				for(WatchEvent<?> event : key.pollEvents()){
					Kind<?> kind = event.kind();
					if(kind.name().equals(StandardWatchEventKinds.ENTRY_MODIFY.name())){
						loader.reload(pathStr + File.separator  + event.context().toString());
					}
					logger.debug("A "+kind+" example is detected on "+event.context().toString());
				}
				boolean reset = key.reset();
				if(!reset){
					break;
				}
			}
		}catch(Exception e){
			e.printStackTrace();
			logger.error(e);
		}
	}
}
