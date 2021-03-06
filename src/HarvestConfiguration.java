import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import rfharvester.ExitCodes;
import rfharvester.download.CSVURLDownloader;
import rfharvester.download.OAIDownloader;
import rfharvester.download.ONIXDownloader;
import rfharvester.download.RFHarvesterDownloaderInterfaceV2;
import rfharvester.download.RFHarvesterDownloaderV2Exception;
import rfharvester.indexor.IndexorException;
import rfharvester.indexor.IndexorInterface;
import rfharvester.indexor.IndexorSolr5;
import rfharvester.logger.RFHarvesterLogger;
import rfharvester.logger.RFHarvesterState;
import rfharvester.transformator.RFHarvesterCodeTransformator;
import rfharvester.transformator.RFHarvesterTransformatorInterfaceV2;
import rfharvester.upload.RFHarvesterUploaderV2Bundle;
import rfharvester.upload.RFHarvesterUploaderV2Exception;
import rfharvester.upload.RFHarvesterUploaderV2Interface;
import rfharvester.upload.UploadCollectionsMySQL5V2;
import rfharvester.upload.UploadControlsMySQL5V2;
import rfharvester.upload.UploadMetadatasMySQL5V2;

public class HarvestConfiguration
{
	private final static String uploadDB = "jdbc:mysql://127.0.0.1/bpiharvester";
	private final static String uploadDBlogin = "root";
	private final static String uploadDBpassword = "password";
	private static Connection DBConnection = null;

	private String downloadURL;
	private String downloadURLADDITION;
	private String CSVSeparator;
	private String harvesterID;
	private String collectionID;
	private String collectionName;
	private String transformationCode;
	private String defaultDocumentType;

	private HashMap<String, String> disponibilite;

	private RFHarvesterDownloaderInterfaceV2 downloader;
	private RFHarvesterTransformatorInterfaceV2 transformator;
	private RFHarvesterUploaderV2Interface uploader;
	private IndexorInterface indexor;

	public HarvestConfiguration() throws SQLException, ClassNotFoundException
	{
		String jdbc = "Fatal error NOJDBC";
		String curDB = "Fatal error NODBNAME";
		try
		{
			jdbc = "com.mysql.jdbc.Driver";
			RFHarvesterLogger.info("Init " + jdbc);
			Class.forName(jdbc);
			DBConnection = DriverManager.getConnection(uploadDB, uploadDBlogin, uploadDBpassword);
			RFHarvesterLogger.info("Connection with " + uploadDB + " established");
		}
		catch(ClassNotFoundException e)
		{
			RFHarvesterLogger.error(jdbc + "not found");
			throw e; // Program won't run with missing library.
		}
		catch(SQLException e)
		{
			RFHarvesterLogger.error("Unable to establish connection with" + curDB);
			throw e;
		}
		disponibilite = new HashMap<String, String>();
		disponibilite.put("dispo_sur_poste", "");
		disponibilite.put("dispo_bibliotheque", "");
		disponibilite.put("dispo_access_libre", "");
		disponibilite.put("dispo_avec_reservation", "");
		disponibilite.put("dispo_avec_access_autorise", "");
		disponibilite.put("dispo_broadcast_group", "");
	}

	public void prepareProgram() throws HarvestConfigurationException, RFHarvesterUploaderV2Exception, SQLException, ClassNotFoundException, IndexorException
	{
		if(RFHarvesterState.checkRunningStatus() != 1)
		{
			RFHarvesterState.updateStatus("ERREUR!!!");
			RFHarvesterState.updateMessage("Impossible de lancer une moissons tant que l'historique contient des moissons ayant le statut: 'En cours'");
			System.exit(ExitCodes.EX_NOPERM.value());
		}
		int collectionID = Integer.parseInt(this.collectionID);
		int recomandedCommit = 1000;

		String MySQLUploadDB = "10.1.2.108/lf_prod";
		UploadControlsMySQL5V2 ControlsUploader = new UploadControlsMySQL5V2(MySQLUploadDB, recomandedCommit, collectionID, collectionName);
		UploadMetadatasMySQL5V2 MetadatasUploader = new UploadMetadatasMySQL5V2(MySQLUploadDB, recomandedCommit, collectionID);
		UploadCollectionsMySQL5V2 CollectionsUploader = new UploadCollectionsMySQL5V2(MySQLUploadDB, collectionID);

		transformator = new RFHarvesterCodeTransformator(transformationCode);
		indexor = new IndexorSolr5(MySQLUploadDB, recomandedCommit, collectionID, collectionName, disponibilite, defaultDocumentType);

		switch(harvesterID)
		{
			case "1": //Portfolio
				/*UploadPortfolioDatasMySQL5V2 PortfolioDatasUploader = new UploadPortfolioDatasMySQL5V2(MySQLUploadDB, recomandedCommit, collectionID);
				UploadVolumesMySQL5V2 VolumesUploader = new UploadVolumesMySQL5V2(MySQLUploadDB, recomandedCommit, collectionID);

				uploader = new RFHarvesterUploaderV2Bundle(ControlsUploader, MetadatasUploader, CollectionsUploader);
				downloader = new PortfolioDownloader(downloadURL, downloadURLADDITION, transformator, uploader, defaultDocumentType);*/
			break;
			case "3": //OAI_DC
				uploader = new RFHarvesterUploaderV2Bundle(ControlsUploader, MetadatasUploader, CollectionsUploader);
				downloader = new OAIDownloader(downloadURL, downloadURLADDITION, transformator, uploader, defaultDocumentType);
			break;
			case "4": //ONIX_DC
				uploader = new RFHarvesterUploaderV2Bundle(ControlsUploader, MetadatasUploader, CollectionsUploader);
				downloader = new ONIXDownloader(downloadURL, downloadURLADDITION, transformator, uploader, defaultDocumentType);
			break;
			case "5": //CSV
				uploader = new RFHarvesterUploaderV2Bundle(ControlsUploader, MetadatasUploader, CollectionsUploader);
				downloader = new CSVURLDownloader(downloadURL, CSVSeparator, transformator, uploader);
			break;
			default:
				throw new HarvestConfigurationException("Unsetted harvester " + harvesterID);
		}
	}

	public void loadConfiguration(int ID) throws HarvestConfigurationException, RFHarvesterUploaderV2Exception, SQLException, ClassNotFoundException, IndexorException
	{
		RFHarvesterLogger.info("Loading configuration " + ID);
		int type = ResultSet.TYPE_FORWARD_ONLY;
		int mode = ResultSet.CONCUR_READ_ONLY;
		Statement DBStatement = null;
		DBStatement = DBConnection.createStatement(type, mode);
		String query = "SELECT configuration.ID, configuration.harvester_ID, configuration.collection_ID, configuration.name, configuration.URL, configuration.URLADDITION, configuration.csv_separator, configuration.dispo_sur_poste, configuration.dispo_bibliotheque, configuration.dispo_access_libre, configuration.dispo_avec_reservation, configuration.dispo_avec_access_autorise, configuration.dispo_broadcast_group, document_type.type, transformation.code FROM configuration INNER JOIN transformation ON configuration.transformation_ID = transformation.ID INNER JOIN document_type ON configuration.default_document_type_ID = document_type.ID WHERE configuration.ID = " + ID;
		RFHarvesterLogger.debug(query);
		ResultSet rs = DBStatement.executeQuery(query);
		int i = 0;

		while(rs.next())
		{
			if((++i) != 1) //If COUNT(*) ResultSet's size !=1 then error
			{
				throw new HarvestConfigurationException("Line " + Thread.currentThread().getStackTrace()[1].getLineNumber() + " : " + i + " results in SELECT COUNT(*) ResutlSet");
			}

			downloadURL = rs.getString("URL");
			downloadURLADDITION = rs.getString("URLADDITION");
			CSVSeparator = rs.getString("csv_separator");

			harvesterID = rs.getString("harvester_ID");
			collectionID = rs.getString("collection_ID");
			if(collectionID==null)
				collectionID = rs.getString("ID");
			collectionName = rs.getString("name");
			RFHarvesterState.updateConfiguration(collectionName);
			transformationCode = rs.getString("code");
			defaultDocumentType = rs.getString("type");

			disponibilite.put("dispo_sur_poste", rs.getString("dispo_sur_poste"));
			disponibilite.put("dispo_bibliotheque", rs.getString("dispo_bibliotheque"));
			disponibilite.put("dispo_access_libre", rs.getString("dispo_access_libre"));
			disponibilite.put("dispo_avec_reservation", rs.getString("dispo_avec_reservation"));
			disponibilite.put("dispo_avec_access_autorise", rs.getString("dispo_avec_access_autorise"));
			disponibilite.put("dispo_broadcast_group", rs.getString("dispo_broadcast_group"));
		}
		rs.close();
		if(i <= 0)
		{
			throw new HarvestConfigurationException("Configuration ID(" + ID + ") not found!!!");
		}

		prepareProgram();
	}

	public void run() throws RFHarvesterDownloaderV2Exception, RFHarvesterUploaderV2Exception, IndexorException
	{
		RFHarvesterLogger.info("I/O");
		downloader.download();
		uploader.end();
		RFHarvesterLogger.info("Indexation");
		try
		{
			indexor.indexUploads();
		}
		catch (IndexorException e)
		{
			e.printStackTrace();
			RFHarvesterLogger.error("Unable to index downloaded datas!!! Operation aborded:" + RFHarvesterLogger.exceptionToString(e));
			throw e;
		}
		RFHarvesterLogger.info("Confirmation");
		uploader.confirm();
	}
}
