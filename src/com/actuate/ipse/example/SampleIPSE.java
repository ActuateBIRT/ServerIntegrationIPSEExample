package com.actuate.ipse.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import com.actuate.iportal.common.IPortalConsts;
import com.actuate.iportal.security.iPortalSecurityAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class SampleIPSE extends iPortalSecurityAdapter {

	// ##########################################################################
	// ################ Static members ##########################################
	// ##########################################################################

	private static String JSON_DATA_FILE_NAME = "SampleIPSE.json";
	private static String DATA_FOLDER = "C:";
	private static String LOG_FILE_NAME = "SampleIPSE.log";
	private static String PROPERTIES_FILE_NAME = "SampleIPSE.properties";
	private static File LOG_FILE;
	private static boolean USE_EXTENDED_CREDENTIALS = false;

	private static PrintStream logStream;
	private static DateFormat df;

	// main volume contents storage
	private static volatile Map<String, IPSEVolume> volumes;

	// the following block is called only once when class gets loaded for the first time
	static {
		// read IPSE driver properties from properties file
		try {
			SampleIPSE.log("static{}: read " + SampleIPSE.PROPERTIES_FILE_NAME + " file");
//			InputStream propStream = SampleIPSE.class.getClassLoader().getResourceAsStream(SampleIPSE.PROPERTIES_FILE_NAME);
			InputStream propStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(SampleIPSE.PROPERTIES_FILE_NAME);
			if (propStream == null) {
				SampleIPSE.throwException("Property file " + SampleIPSE.PROPERTIES_FILE_NAME + " not found.");
			} else {
				Properties props = new Properties();
				props.load(propStream);
				if (props.size() <= 0) {
					SampleIPSE.throwException("Cannot read from " + SampleIPSE.PROPERTIES_FILE_NAME);
				}
				for (String p : props.stringPropertyNames()) {
					if (p.compareToIgnoreCase("DATA_FOLDER") == 0)
						SampleIPSE.DATA_FOLDER = (String) props.getProperty(p);
					else if (p.compareToIgnoreCase("LOG_FILE_NAME") == 0)
						SampleIPSE.LOG_FILE_NAME = (String) props.getProperty(p);
					else if (p.compareToIgnoreCase("JSON_DATA_FILE_NAME") == 0)
						SampleIPSE.JSON_DATA_FILE_NAME = (String) props.getProperty(p);
					else if (p.compareToIgnoreCase("USE_EXTENDED_CREDENTIALS") == 0)
						SampleIPSE.USE_EXTENDED_CREDENTIALS = Boolean.parseBoolean(((String) props.getProperty(p)));
				}
			}
		} catch (IOException e) {
			SampleIPSE.log(e, "I/O exception while reading property file");
		} catch (Exception e) {
			SampleIPSE.log(e, "General exception while reading property file");
		}

		// initialize logging
		try {
			SampleIPSE.log("static{}: initialize logging");
			SampleIPSE.LOG_FILE = new File(SampleIPSE.DATA_FOLDER + "/" + SampleIPSE.LOG_FILE_NAME);
			if (SampleIPSE.LOG_FILE.exists()) {
				SampleIPSE.LOG_FILE.renameTo(new File(SampleIPSE.DATA_FOLDER + "/" + SampleIPSE.LOG_FILE_NAME + ".bak"));
			}
			SampleIPSE.LOG_FILE.createNewFile();
			SampleIPSE.LOG_FILE.setWritable(true);
			SampleIPSE.logStream = new PrintStream(SampleIPSE.DATA_FOLDER + "/" + SampleIPSE.LOG_FILE_NAME);
			SampleIPSE.log("Switch logging to " + SampleIPSE.LOG_FILE + ". All further log records will go to that file.");
			System.setOut(SampleIPSE.logStream);
			System.setErr(SampleIPSE.logStream);
			SampleIPSE.df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
			SampleIPSE.log("Start log");
		} catch (FileNotFoundException e) {
			SampleIPSE.log(e, "FileNotFoundException while creating " + SampleIPSE.LOG_FILE.getAbsolutePath() + " file");
		} catch (IOException e) {
			SampleIPSE.log(e, "I/O exception while creating " + SampleIPSE.LOG_FILE.getAbsolutePath() + " file");
		}

		// read URL and repository user credentials from a JSON file, if provided
		try {
			SampleIPSE.log("static{}: read data from file" + SampleIPSE.DATA_FOLDER + "/"
					+ SampleIPSE.JSON_DATA_FILE_NAME);
			SampleIPSE.volumes = SampleIPSE.readDataFromFile(SampleIPSE.DATA_FOLDER + "/"
					+ SampleIPSE.JSON_DATA_FILE_NAME);
		} catch (Exception e) {
			SampleIPSE.log(e, "Cannot read data from file " + SampleIPSE.DATA_FOLDER + "/"
					+ SampleIPSE.JSON_DATA_FILE_NAME);
		}
	}

	synchronized public static IPSEVolume getAuthVolume(String volumeName) {
		SampleIPSE.log("getAuthVolume(): volume=" + volumeName);
		String key = volumeName.toUpperCase();
		if (SampleIPSE.volumes.containsKey(key)) {
			SampleIPSE.log("getAuthVolume(): Found volume " + volumeName + " in data storage");
			return SampleIPSE.volumes.get(key);
		} else {
			SampleIPSE.log("getAuthVolume(): volume " + volumeName + " not found");
			return null;
		}
	}

	// ##########################################################################
	// ################ Per-instance members ####################################
	// ##########################################################################

	private final String AUTH_SESSION_KEY = "APSE_AUTH";
	private String username = null;
	private String password = null;
	private String serverUrl = null;
	private String volume = null;
	private String homeFolder = null;
	private String volumeProfile = null;
	private String repositoryType = null;

	// There should be no explicit constructor
//	 public SampleIPSE() {
//	 SampleIPSE.log("SampleIPSE()");
//	 }
	
	// ##########################################################################
	// ################ Interface methods #######################################
	// ##########################################################################

	/**
	 * Evaluates the current user's security credentials.<br>
	 * 
	 * The iPortal authentication module calls authenticate() to validate the
	 * current user's security credentials. authenticate() retrieves the
	 * credentials information sent by the browser to the calling page and
	 * transform it into valid credentials to connect to a repository. The
	 * authentication module calls authenticate() whenever credentials are
	 * required, switching between type of repositories or connecting to a
	 * different repository (changing volume in the Enterprise mode). If
	 * authenticate() returns False, the user is redirected to the login page.
	 * 
	 * @param request
	 *            the servlet
	 * @return True for successful credential evaluation and False otherwise. If
	 *         credential evaluation is not successful throw an
	 *         AuthenticationException indicating the reason for the failure.
	 */
	public boolean authenticate(HttpServletRequest request) {
		SampleIPSE.log("authenticate()");

		this.repositoryType = request.getParameter("repositoryType");
		SampleIPSE.log("authenticate(): repositoryType=" + this.repositoryType);

		if (!this.isEnterprise()) {
			ServletContext servletContext = request.getSession().getServletContext();
			// this.homeFolder = servletcontext.getInitParameter("STANDALONE_HOME_FOLDER");
			this.homeFolder = servletContext.getInitParameter(IPortalConsts.WEBXML_STANDALONE_HOME_FOLDER);
			SampleIPSE.log("authenticate(): homeFolder=" + this.homeFolder);
		}

		this.volumeProfile = request.getParameter("__vp");
		SampleIPSE.log("authenticate(): volumeProfile=" + this.volumeProfile);

		String v = request.getParameter("volume");
		IPSEVolume volume = (v == null) ? SampleIPSE.getAuthVolume("DEFAULT VOLUME") : SampleIPSE.getAuthVolume(v);
		SampleIPSE.log("authenticate(): volume=" + v);
		if (volume == null) {
			return false;
		}

		String u = request.getParameter("userid");
		SampleIPSE.log("authenticate(): userid=" + u);
		if (u == null || u.length() <= 0) {
			return false;
		}
		String p = request.getParameter("password");
		// uncomment the following line to see the password in the log
//		SampleIPSE.log("authenticate(): password=" + p);
		
		// validate whether credentials are correct
		boolean credentialsAreValid = true;
		Map<String, IPSEUser> users = volume.getUsers();
		if (users == null || !users.containsKey(u.toUpperCase())) {
			// user is not found
			credentialsAreValid = false;
		} else {
			IPSEUser user = users.get(u.toUpperCase());
			this.username = user.getName();
			this.password = user.getPassword();
			String pwd = user.getAuthPassword();
			if (pwd == null && p == null) {
				credentialsAreValid = true;
			}
			if (pwd == null && p != null || pwd != null && p == null || pwd.length() != p.length()) {
				credentialsAreValid = false;
			}
			// switch the following two lines to see passwords in the log
//			SampleIPSE.log("authenticate(): compare password in request VS authentication password from user data: " + p + " VS " + pwd);
			SampleIPSE.log("authenticate(): compare password in request and authentication password from user data");
			if(!pwd.equals(p)) {
				credentialsAreValid = false;
			}
			SampleIPSE.log("authenticate(): credentials are " + (credentialsAreValid?"valid":"invalid"));
		}
		
		// Administrator can perform certain actions after successful authorization.
		// The example below shows how to reload the in-memory user data from file:
		// this can be done by passing an additional parameter adminOperation.
		if(credentialsAreValid && "Administrator".equalsIgnoreCase(this.username)) {
			String adminOperation = request.getParameter("adminOperation");
			if(adminOperation != null && adminOperation.equalsIgnoreCase("reload")) {
				// reload data from JSON file
				SampleIPSE.log("authenticate(): reload data from file " + SampleIPSE.DATA_FOLDER + "/"
						+ SampleIPSE.JSON_DATA_FILE_NAME);
				try {
					SampleIPSE.volumes = SampleIPSE.readDataFromFile(SampleIPSE.DATA_FOLDER + "/"
							+ SampleIPSE.JSON_DATA_FILE_NAME);
				} catch (Exception e) {
					SampleIPSE.log(e, "Cannot read data from file " + SampleIPSE.DATA_FOLDER + "/"
							+ SampleIPSE.JSON_DATA_FILE_NAME);
				}
			}
			
		}
		
		return credentialsAreValid;
	}

	/**
	 * Retrieves the current user's login name.<br>
	 * 
	 * The authentication module calls getUserName() to retrieve the current
	 * user's login name. The authentication module uses the login name to
	 * establish a connection to the repository.
	 * 
	 * @return A string containing the user name that will be valid for the
	 *         repository.
	 */
	public String getUserName() {
		SampleIPSE.log("getUserName():" + this.username);
		return this.username;
	}

	/**
	 * Retrieves the current user's password.<br>
	 * 
	 * The authentication module calls getPassword() to retrieve the current
	 * user's password. The authentication module uses the password to establish
	 * a connection to the repository.
	 * The password will not be returned (null) if extended credentials are to
	 * be used. The extended credentials must be interpreted by RSSE driver on
	 * the backend for authentication purposes instead.
	 * 
	 * @return A string that is the password to use to establish the connection
	 *         to the repository, or null if there is no password for the user.
	 */
	public String getPassword() {
		String res = null;
		if (SampleIPSE.USE_EXTENDED_CREDENTIALS) {
			res = null;
		} else {
			res = this.password;
		}
		// switch the following two lines to see the password in the log
//		SampleIPSE.log("getPassword():" + res);
		SampleIPSE.log("getPassword()");
		return res;
	}

	/**
	 * Retrieves the volume to which the current user connects.<br>
	 * The Login module calls getVolume() to retrieve the name of the
	 * Encyclopedia volume to which the user wishes to connect. This is only for
	 * <b>Enterprise</b> mode.
	 * 
	 * @return A string containing the domain and volume name for the
	 *         Encyclopedia volume to which the user connects to through the
	 *         Actuate iServer. If null, the Actuate iServer connects to the
	 *         default volume
	 */
	public String getVolume() {
		SampleIPSE.log("getVolume():" + this.volume);
		return this.volume;
	}

	/**
	 * Retrieves serverurl to which the current user connects.<br>
	 * 
	 * @return A string containing the serverurl to which the user connects.
	 */
	public String getServerUrl() {
		SampleIPSE.log("getServerUrl(): " + this.serverUrl);
		return this.serverUrl;
	}

	/**
	 * Retrieves repository type to which the current user connects.<br>
	 * 
	 * @return A string containing the repositoryType to which the user
	 *         connects.
	 */
	public String getRepositoryType() {
		SampleIPSE.log("getRepositoryType(): " + this.repositoryType);
		if(this.repositoryType == null) {
			return "Enterprise";
		}
		return this.repositoryType;
	}

	/**
	 * Retrieves the current user's extended security credentials.<br>
	 * 
	 * This method returns the extended credentials the Actuate iServer
	 * requires. A page calls the method to send the extended credentials the
	 * Actuate iServer requires for user authentication. This is only intended
	 * for the <b>Enterprise</b> mode.
	 * <br>
	 * This particular example shows how to use extended credentials for secure
	 * authentication against iHub using RSSE. The extended credentials get
	 * encrypted here, so RSSE could read them and decrypt using reverse
	 * algorithm or key. This way the password is not sent over the network in
	 * plain text. The "encryption" used here for demo purposes is a simple
	 * inversion.
	 * 
	 * @return a byte array representing any extended credentials for the
	 *         Actuate iServer to use to authenticate the user, or null if there
	 *         are no extended credentials to evaluate.
	 */
	public byte[] getExtendedCredentials() {
		byte[] res = null;
		if (SampleIPSE.USE_EXTENDED_CREDENTIALS) {
			byte[] pwd = this.password.getBytes();
			res = new byte[pwd.length];
			for (int i = 0; i < pwd.length; i++) {
				res[res.length - i - 1] = pwd[i];
			}
		}
		// switch the following two lines to see credentials in the log
		//SampleIPSE.log("getExtendedCredentials(): " + ((res == null) ? "null" : (new String(res))));
		SampleIPSE.log("getExtendedCredentials()");
		return res;
	}

	/**
	 * Determines whether we should connect to iServer, in other words if we
	 * will be working in the Enterprise mode or in the Workgroup mode.
	 * 
	 * @return True if we should connect to iServer.
	 */
	public boolean isEnterprise() {
		boolean enterpriseMode = true;
		String repositoryType = this.getRepositoryType();
		if ("workgroup".equalsIgnoreCase(repositoryType)) {
			enterpriseMode = false;
		}
		SampleIPSE.log("isEnterprise(): " + enterpriseMode);
		return enterpriseMode;
	}

	/**
	 * Retrieves the current user home folder location to be used within the
	 * repository. This is only intended for the <b>Workgroup</b> mode.
	 * 
	 * @return properties to be used by the authentication module
	 */
	public String getUserHomeFolder() {
		String res = null;
		if (this.homeFolder != null) {
			res = this.homeFolder + "/" + this.getUserName();
		}
		SampleIPSE.log("getUserHomeFolder(): " + res);
		return res;
	}

	public String getVolumeProfile() {
		SampleIPSE.log("getVolumeProfile(): " + null);
		return null;
	}

	public String getDashboardTemplate() {
		SampleIPSE.log("getDashboardTemplate(): " + null);
		return null;
	}

	/**
	 * Gets the runAsUser value for this Login.
	 * 
	 * @return runAsUser
	 */
	public String getRunAsUser() {
		SampleIPSE.log("getRunAsUser(): " + null);
		return null;
	}

	
	// ##########################################################################
	// ################ class IPSEUser ##########################################
	// ##########################################################################

	private class IPSEUser {

		// class members
		private String name;
		private String password;
		private String authName;
		private String authPassword;

		// constructors
		public IPSEUser() {
			SampleIPSE.log("IPSEUser()");
		}

		// getters/setters
		public String getName() {
			SampleIPSE.log("getName(): name=" + this.name);
			return this.name;
		}

		public void setName(String name) {
			SampleIPSE.log("setName(): name=" + name);
			this.name = name;
		}

		public String getPassword() {
			// use the following line if you want to see the password in the log
			//SampleIPSE.log("getPassword(): password=" + this.password);
			SampleIPSE.log("getPassword()");
			return this.password;
		}

		public void setPassword(String password) {
			// use the following line if you want to see the password in the log
			//SampleIPSE.log("setPassword(): password=" + password);
			SampleIPSE.log("setPassword()");
			this.password = password;
		}

		public String getAuthName() {
			SampleIPSE.log("getAuthName(): authName=" + this.authName);
			return this.authName;
		}

		public void setAuthName(String authName) {
			SampleIPSE.log("setAuthName(): authName=" + authName);
			this.authName = authName;
		}

		public String getAuthPassword() {
			// use the following line if you want to see the password in the log
			//SampleIPSE.log("getAuthPassword(): authPassword=" + this.authPassword);
			SampleIPSE.log("getAuthPassword()");
			return this.authPassword;
		}

		public void setAuthPassword(String authPassword) {
			// use the following line if you want to see the password in the log
			//SampleIPSE.log("setAuthPassword(): authPassword=" + authPassword);
			SampleIPSE.log("setAuthPassword()");
			this.authPassword = authPassword;
		}
	}

	// ##########################################################################
	// ################ class IPSEVolume ########################################
	// ##########################################################################

	class IPSEVolume {

		// class members
		private String name;
		private Map<String, IPSEUser> users;

		// constructors
		public IPSEVolume(String name) {
			this.setName(name.toUpperCase());
			this.setUsers(new ConcurrentHashMap<String, IPSEUser>());
		}

		// getters/setters
		public String getName() {
			SampleIPSE.log("getName(): name=" + this.name);
			return this.name;
		}

		public void setName(String name) {
			SampleIPSE.log("setName(): name=" + name);
			this.name = name;
		}

		public Map<String, IPSEUser> getUsers() {
			SampleIPSE.log("getUsers()");
			return this.users;
		}

		public void setUsers(Map<String, IPSEUser> users) {
			SampleIPSE.log("setUsers()");
			this.users = users;
		}

		public void addUser(IPSEUser user) {
			SampleIPSE.log("addUser()");
			if (!this.users.containsKey(user.getName().toUpperCase())) {
				// user names are cases insensitive, so store the key as
				// uppercase
				this.users.put(user.getName().toUpperCase(), user);
			}
		}
	}

	// ##########################################################################
	// ################ Helper methods ##########################################
	// ##########################################################################

	private synchronized static ConcurrentHashMap<String, IPSEVolume> readDataFromFile(String filePath) throws Exception {
		SampleIPSE.log("readDataFromFile(): filePath=" + filePath);
		ConcurrentHashMap<String, IPSEVolume> data = null;
		try {
			File jsonFile = new File(filePath);
			if (jsonFile.exists()) {
				SampleIPSE.log("Bulk load JSON file is found: " + SampleIPSE.JSON_DATA_FILE_NAME);
				Gson gson = new Gson();
				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(SampleIPSE.DATA_FOLDER
						+ "/" + SampleIPSE.JSON_DATA_FILE_NAME), "UTF8"));
				data = gson.fromJson(br, (new TypeToken<ConcurrentHashMap<String, IPSEVolume>>() {
				}).getType());
				br.close();
			} else {
				SampleIPSE.log("JSON file " + SampleIPSE.JSON_DATA_FILE_NAME
						+ " not found. Creating vanilla instance.");
			}
		} catch (IOException e) {
			SampleIPSE.throwException(e, "I/O exception while reading JSON file");
		} catch (Exception e) {
			SampleIPSE.throwException(e, "General exception while reading JSON file");
		}
		return data;
	}

	private synchronized static void saveDataToFile(ConcurrentHashMap<String, IPSEVolume> data) throws Exception {
		SampleIPSE.log("saveDataToFile()");
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			File file = new File(SampleIPSE.DATA_FOLDER + "/" + SampleIPSE.JSON_DATA_FILE_NAME);
			Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
			out.write(gson.toJson(data, (new TypeToken<ConcurrentHashMap<String, IPSEVolume>>() {
			}).getType()));
			out.flush();
			out.close();
			SampleIPSE.log("saveDataToFile(): contents were saved to JSON file");
		} catch (UnsupportedEncodingException e) {
			SampleIPSE.throwException("Cannot save JSON data to a file");
		} catch (IOException e) {
			SampleIPSE.throwException("Cannot save JSON data to a file");
		}
	}

	private static void throwException(String message) throws Exception {
		SampleIPSE.log(message);
		throw new Exception(message);
	}

	private static void throwException(Exception e, String message) throws Exception {
		SampleIPSE.log(e, message);
		throw new Exception(message, e);
	}

	synchronized private static void log(String strToLog) {
		if (SampleIPSE.df == null) {
			SampleIPSE.df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
		}
		SampleIPSE.df.format(new Date(System.currentTimeMillis()));
		System.out.println(SampleIPSE.df.format(new Date(System.currentTimeMillis())) + ": " + strToLog);
	}

	synchronized private static void log(Exception e, String strToLog) {
		if (SampleIPSE.df == null) {
			SampleIPSE.df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
		}
		System.out.println(SampleIPSE.df.format(new Date(System.currentTimeMillis())) + ": ERROR: " + strToLog);
		e.printStackTrace(SampleIPSE.logStream);
	}
}
