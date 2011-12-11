/*
 * This file is part of IRCBot.
 * Copyright (c) 2011 Ryan Morrison
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions, and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions, and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *  * Neither the name of the author of this software nor the name of
 *  contributors to this software may be used to endorse or promote products
 *  derived from this software without specific prior written consent.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */

package us.rddt.IRCBot;

import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.QuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

public class SeenHandler implements Runnable {
	// Variables
	private MessageEvent event;
	private PartEvent pEvent;
	private QuitEvent qEvent;
	private boolean hasParted;
	private String seenUser;

	private Database database;

	// Method that executes upon start of thread
	public void run() {
		// We've received a direct !seen command
		if(!hasParted) {
			searchUser();
			return;
		}
		// We've received a notification that a user has left a channel
		else {
			updateSeen();
			return;
		}
	}

	// Class constructor
	public SeenHandler(MessageEvent event) {
		this.event = event;
	}

	// Overloaded class constructor used for parting events
	public SeenHandler(PartEvent pEvent) {
		this.pEvent = pEvent;
		this.hasParted = true;
	}

	// Overloaded class constructor used for parting events
	public SeenHandler(QuitEvent event) {
		this.qEvent = event;
		this.hasParted = true;
	}

	// Method to search the key/value store for a user
	private void searchUser() {
		// Extract the user name from the command and remove any unnecessary whitespace
		try {
			seenUser = event.getMessage().substring(6).replaceAll("^\\s+", "").replaceAll("\\s+$", "");
		} catch (IndexOutOfBoundsException ex) {
			return;
		}
		// The user is performing the command on themselves?
		if(seenUser.equals(event.getUser().getNick())) {
			event.respond("What are you doing?");
			return;
		// The user is performing the command on the bot?
		} else if (seenUser.equals(event.getBot().getNick())) {
			event.respond("I don't think that command means what you think it means.");
			return;
		// Make sure the user isn't in the channel, if they are then just return that they are
		} else if (event.getBot().getUsers(event.getChannel()).contains(event.getBot().getUser(seenUser))) {
			event.respond(seenUser + " is currently in the channel.");
			return;
		// Make sure we don't have a blank request
		} else if(seenUser.equals("")) {
			event.respond("I can't see when a user was last here if you don't give me one!");
			return;
		// If all else fails, we have a valid request
		} else {
		// Create a new instance of the database
			database = new Database();
			try {
				// Connect to the database and execute our select query
				database.connect();
				PreparedStatement statement = database.getPreparedStatement();
				statement = database.getConnection().prepareStatement("SELECT Date FROM Seen WHERE Nick = ?");
				statement.setString(1, seenUser);
				ResultSet resultSet = statement.executeQuery();
				// Respond appropriately should our user exist/not exist in the database
				if(resultSet.next()) {
					event.respond(seenUser + " was last seen about " + IRCUtils.toReadableTime(resultSet.getTimestamp("Date"), false) + " ago.");
				} else {
					event.respond("I haven't seen " + seenUser + ".");
				}
				// Disconnect from the database
				database.disconnect();
			} catch (Exception ex) {
				IRCUtils.Log(IRCUtils.LOG_ERROR, ex.getMessage());
				ex.printStackTrace();
			}
		}
	}

	// Method called when a user parts/quits. Update their key/value with the timestamp of when they left.
	private void updateSeen() {
		// Temporary variable
		String userToUpdate;
		// Make sure we have our user and channel to update, regardless of the event passed
		if(pEvent != null) {
			userToUpdate = pEvent.getUser().getNick();
		}
		else {
			userToUpdate = qEvent.getUser().getNick();
		}

		// Create a new instance of the database
		database = new Database();
		try {
			// Connect to the database and execute our select query to see whether to insert or update
			database.connect();
			PreparedStatement statement = database.getPreparedStatement();
			statement = database.getConnection().prepareStatement("SELECT Date FROM Seen WHERE Nick = ?");
			statement.setString(1, userToUpdate);
			ResultSet resultSet = statement.executeQuery();
			// If a record exists, then run another query to update the date appropriately
			if(resultSet.next()) {
				statement = database.getConnection().prepareStatement("UPDATE Seen SET Date = ? WHERE Nick = ?");
				statement.setTimestamp(1, new java.sql.Timestamp(new Date().getTime()));
				statement.setString(2, userToUpdate);
				statement.executeUpdate();
			}
			// Otherwise, create a new record in the database for the user
			else {
				statement = database.getConnection().prepareStatement("INSERT INTO Seen(Nick, Date, Channel) VALUES (?, ?)");
				statement.setString(1, userToUpdate);
				statement.setTimestamp(2, new java.sql.Timestamp(new Date().getTime()));
				statement.executeUpdate();
			}
			// Disconnect from the database
			database.disconnect();
		} catch (Exception ex) {
			IRCUtils.Log(IRCUtils.LOG_ERROR, ex.getMessage());
			ex.printStackTrace();
		}
	}
}
