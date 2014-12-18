#Server Integration IPSE Example

##Overview

This is a sample iPortal Security Extension (IPSE) implementation. It uses JSON-formatted data to define mappings for user credentials passed in URLs and iHub volume user properties. This sample implementation can be used in conjunction with the sample RSSE implementation.

##Setup

1. Open the file 'src\SampleIPSE.properties' in an editor. This file contains the following config properties, which override default values in the example driver code:
  * DATA_FOLDER - Folder that contains the JSON-formatted data file containing the user name/password mappings. A sample JSON file is provided.
  * JSON_STORAGE_FILE_NAME - Name of the JSON-formatted data file containing the user name/password mappings.
  * LOG_FILE_NAME - Name of the log file the IPSE driver creates in DATA_FOLDER.
  * USE_EXTENDED_CREDENTIALS - Specifies whether the IPSE driver uses extended credentials or user password. One is null when the other is defined. Extended credentials are passed to the RSSE-enabled iHub in the IDAPI Login and RSSE API authenticate() requests. Extended credentials are designed for passing encrypted authentication information to RSSE, which RSSE interprets. The current SampleRSSE example fully supports this functionality and can be used in conjunction with the current IPSE example.
2. Create the folder that DATA_FOLDER specifies.
3. Copy the file that JSON_STORAGE_FILE_NAME specifies to the new folder. A log file will also be created in this folder later on.

###Data File Structure

Data file contains mappings between the authentication credentials passed in a URL and the actual user name and password in the iHub volume. Data file has the following structure, which reflects the class hierarchy in the sample driver. Do not change the structure without changing the respective classes in the sample implementation first:

```
"DEFAULT VOLUME": {				<== capitalized volume name; used as a hash key
	"name": "Default Volume",	<== actual volume name
	"users": {					<== user list in the volume
		"USER1": {				<== capitalized name of the user; used as a key
			"name": "User1",	<== user name in iHub volume
			"password": "pass",	<== user password in iHub volume
			"authName": "u1",	<== user name passed in iPortal URL
			"authPassword": "1"	<== user password passed in iPortal URL
		},
		...
```

Also make sure that users listed in the JSON file exist in the iHub volume before you load the file.

###Build

1. Copy Java Server JAR file 'iHub\Jar\javaserver.jar' from the iHub installation to the '/lib' folder in the example project. Example's Eclipse project classpath already includes this file.
2. Simply run the "ant" command: it will produce the 'SampleIPSE.jar' file in the '/bin' folder.

###Deploy

1. Copy 'SampleIPSE.jar' and all libraries from the '/lib' folder in the example project to folder 'iHub\web\iportal\WEB-INF\lib' in your iHub installation. 
2. Set SECURITY_ADAPTER_CLASS in 'iHub\web\iportal\WEB-INF\web.xml' to 'com.actuate.ipse.example.SampleIPSE'.
3. Restart the iHub node or alternatively, just the Web service in System Console.

##Usage

Pass desired values for URL parameters. The example IPSE implementation uses the same parameter names as the current iPortal login page. They can be changed if necessary. Here are some URL examples:

* To view files/folders as user 'user1':

```
http://localhost:8700/iportal/getfolderitems.do?userid=b1&volume=Volume1&password=1
```

* To reload data file and then view files/folders as 'Administrator':

```
http://localhost:8700/iportal/getfolderitems.do?userid=admin&volume=Default%20Volume&password=pass&adminOperation=reload
```

A user having the name 'Administrator' in the iHub volume can perform additional operations during authorization. One example operation in the sample IPSE implementation supports reloading user name and password pairs from a data file; it is possible to reload data from JSON file without restarting the Web service or the iHub node.


##Troubleshooting

* Track contents of DATA_FOLDER: a log file will be created when SampleIPSE class gets loaded for the first time, which happens when user authorization is required.
* If you see no log file created after you tried several URLs, check the file 'iHub\data\server\log\ihubservletcontainer.{process-id}.{host-name}.{date-time}.0.log' in your iHub installation for errors.
* Read comments in the code. They may provide understanding if you encounter difficulty.