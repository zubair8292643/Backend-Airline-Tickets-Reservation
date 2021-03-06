package dao;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import model.airplane.Airplanes;
import model.airport.Airport;
import model.airport.Airports;
import model.flight.*;

import model.reservation.*;
import utils.QueryFactory;


/**
 * This class provides an interface to the CS509 server. It provides sample methods to perform
 * HTTP GET and HTTP POSTS
 *   
 * @author blake
 * @version 1.1
 * @since 2016-02-24
 *
 */
public enum ServerInterface {
	INSTANCE;
	
	private final String mUrlBase = "http://cs509.cs.wpi.edu:8181/CS509.server/ReservationSystem";

	/**
	 * Return a collection of all the airports from server
	 * 
	 * Retrieve the list of airports available to the specified ticketAgency via HTTPGet of the server
	 * 
	 * @param teamName identifies the name of the team requesting the collection of airports
	 */
	public void getAirports (String teamName) {

		StringBuffer airportResult, timeZoneResult;
		String xmlAirports, xmlAirportTimeZone;

		// Get Airports
		
		airportResult = trySetup(teamName, "airport", null,null,null);
		xmlAirports = airportResult.toString();
		Dao.addAllAirports(xmlAirports);
		
		// get time zones
		for (Airport airport : Airports.getInstance()) {
			String timeZone = airport.quickTimeZone();
			if (Objects.equals(timeZone,"invalid")) {
				timeZoneResult = trySetup("","timezone", airport,null,null);
				xmlAirportTimeZone = timeZoneResult.toString();
				Dao.addAirportTimeZone(xmlAirportTimeZone, airport);
			}
			else {
				airport.timeZone(timeZone);
			}
		}
		//
		
	}
	
	/**
	 * Return a collection of all the airports from server
	 * 
	 * Retrieve the list of airports available to the specified ticketAgency via HTTPGet of the server
	 * 
	 * @param teamName identifies the name of the team requesting the collection of airports
	 * @param departureAirport identifies 3 unique code of departure airport
	 * @param departureDate identifies departure date in mm_dd_yyyy format
	 * @return collection of Airports from server
	 */
	public Flights getFlights (String teamName, String departureAirport, String departureDate) {

		StringBuffer result1 = new StringBuffer();
		StringBuffer result2 = new StringBuffer();
		String xmlFlights1, xmlFlights2;
		Flights flights1, flights2, flightsUnion = new Flights();
		String depDatePlusOne;

		// get flights for date
		result1 = trySetup(teamName,"flight",null,departureAirport,departureDate);
		xmlFlights1 = result1.toString();
		flights1 = Dao.addAllFlights(xmlFlights1);
		
		// get flights for following date to accommodate local time conversion border
		String[] tokens = departureDate.split("_");
		int year = Integer.parseInt(tokens[0]);
		int month = Integer.parseInt(tokens[1]);
		int day = Integer.parseInt(tokens[2]);
		
		if (day != 31) {
			day += 1;
		}
		depDatePlusOne = year + "_" + month + "_" + day;
		
		result2 = trySetup(teamName,"flight",null,departureAirport,depDatePlusOne);
		xmlFlights2 = result2.toString();
		flights2 = Dao.addAllFlights(xmlFlights2);
		
		flightsUnion.addAll(flights1);
		flightsUnion.addAll(flights2);
		
		return flightsUnion;
	}
	
	/**
	 * Return a collection of all the airplanes from server
	 * 
	 * Retrieve the list of airplanes available to the specified ticketAgency via HTTPGet of the server
	 * 
	 * @param teamName identifies the name of the team requesting the collection of airplanes
	 * @return collection of Airplanes from server
	 */
	public Airplanes getAirplanes (String teamName) {

		StringBuffer result;
		String xmlAirplanes;
		Airplanes airplanes;

		result = trySetup(teamName,"airplane",null,null,null);

		xmlAirplanes = result.toString();
		airplanes = Dao.addAllAirplanes(xmlAirplanes);
		return airplanes;
		
	}
	
	/**
	 * Return a string buffer to be parsed by Dao
	 * 
	 * Retrieve the list of airplanes available to the specified ticketAgency via HTTPGet of the server
	 * 
	 * @param teamName identifies the name of the team requesting the collection of airplanes, airports, or flights
	 * @param type identifies if airplane, airport, or flight will be returned
	 * @param airport identifies airport 
	 * @param departureAirport identifies 3 unique code of departure airport
	 * @param departureDate identifies departure date in mm_dd_yyyy format
	 * @return StringBuffer result
	 */
	
	public StringBuffer trySetup (String teamName, String type, Airport airport, String departureAirport, String departureDate) {
		URL url;
		HttpURLConnection connection;
		BufferedReader reader;
		String line;
		StringBuffer result = new StringBuffer();
		
		try {
			/**
			 * Create an HTTP connection to the server for a GET 
			 * for flight, airplane, airport, or timezone
			 */
			if (type == "airplane") {
				url = new URL(mUrlBase + QueryFactory.getAirplanes(teamName));
			}
			else if (type == "flight") {
				url = new URL(mUrlBase + QueryFactory.getFlights(teamName,departureAirport,departureDate));
			}
			else if (type == "airport") {
				url = new URL(mUrlBase + QueryFactory.getAirports(teamName));
			}
			else if (type == "timezone") {
				String query = QueryFactory.getTimeZone(airport.latitude(), airport.longitude());
				url = new URL(query);
			}
			// return nothing if type is not airplane, flight, airport, or timezone
			else {
				return null;
			}
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			if (type != "timezone") connection.setRequestProperty("User-Agent", teamName);

			/**
			 * If response code of SUCCESS read the XML string returned
			 * line by line to build the full return string
			 */
			int responseCode = connection.getResponseCode();
			if (responseCode >= HttpURLConnection.HTTP_OK) {
				InputStream inputStream = connection.getInputStream();
				String encoding = connection.getContentEncoding();
				encoding = (encoding == null ? "UTF-8" : encoding);

				reader = new BufferedReader(new InputStreamReader(inputStream));
				while ((line = reader.readLine()) != null) {
					result.append(line);
				}
				reader.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Lock the database for updating by the specified team. The operation will fail if the lock is held by another team.
	 * 
	 * @param teamName is the name of team requesting server lock
	 * @return true if the server was locked successfully, else false
	 */
	public boolean lock (String teamName) {
		URL url;
		HttpURLConnection connection;

		try {
			url = new URL(mUrlBase);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("User-Agent", teamName);
			connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
			
			String params = QueryFactory.lock(teamName);
			
			connection.setDoOutput(true);
			DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
			writer.writeBytes(params);
			writer.flush();
			writer.close();
			
			BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			StringBuffer response = new StringBuffer();
			
			while ((line = in.readLine()) != null) {
				response.append(line);
			}
			in.close();
			
			System.out.println(response.toString());
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Unlock the database previous locked by specified team. The operation will succeed if the server lock is held by the specified
	 * team or if the server is not currently locked. If the lock is held be another team, the operation will fail.
	 * 
	 * The server interface to unlock the server interface uses HTTP POST protocol
	 * 
	 * @param teamName is the name of the team holding the lock
	 * @return true if the server was successfully unlocked.
	 */
	public boolean unlock (String teamName) {
		URL url;
		HttpURLConnection connection;
		
		try {
			url = new URL(mUrlBase);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			
			String params = QueryFactory.unlock(teamName);
			
			connection.setDoOutput(true);
			connection.setDoInput(true);
			
			DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
			writer.writeBytes(params);
			writer.flush();
			writer.close();
		    
			int responseCode = connection.getResponseCode();
			//System.out.println("\nSending 'POST' to unlock database");
			//System.out.println(("\nResponse Code : " + responseCode));

			if (responseCode >= HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				StringBuffer response = new StringBuffer();

				while ((line = in.readLine()) != null) {
					response.append(line);
				}
				in.close();

				System.out.println(response.toString());
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Update server with flights to be reserved and if coach or first class
	 * 
	 * @param teamName is the name of the team holding the lock
	 * @param reservation identifies the flights reserved by customer
	 * @return true if the update was successful
	 */
	public boolean postFlights (String teamName, Reservation reservation) {
		URL url;
		HttpURLConnection connection;
		
		try {
			url = new URL(mUrlBase);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			
			String params = QueryFactory.updateFlights(teamName,reservation);
			//System.out.println("params= " + params);
			connection.setDoOutput(true);
			connection.setDoInput(true);
			
			DataOutputStream writer = new DataOutputStream(connection.getOutputStream());
			writer.writeBytes(params);
			writer.flush();
			writer.close();
		    
			int responseCode = connection.getResponseCode();
			//System.out.println("\nSending 'POST' to update database");
			//System.out.println(("\nResponse Code : " + responseCode));

			if (responseCode >= HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				StringBuffer response = new StringBuffer();

				while ((line = in.readLine()) != null) {
					response.append(line);
				}
				in.close();

				System.out.println("--------------------------------------------------------");
				System.out.println("		Your reservation has been confirmed by server");
				System.out.println("--------------------------------------------------------");
				System.out.println("\n");
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();
			return false;
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
}