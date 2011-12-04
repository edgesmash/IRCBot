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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.Iterator;

import org.pircbotx.User;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.MessageEvent;

public class KingHandler implements Runnable {
	// Variables
	private static Database database;
	private MessageEvent mEvent;
	private JoinEvent jEvent;
	
	public void run() {
		// We have a new king to set, deop everyone else and set them
		if(mEvent != null) {
			Iterator<User> itr = mEvent.getChannel().getOps().iterator();
			while(itr.hasNext()) {
				User next = (User)itr.next();
				if(!next.equals(mEvent.getBot().getUserBot())) mEvent.getChannel().deOp(next);
			}
			mEvent.getChannel().op(mEvent.getUser());
			setNewKing(mEvent.getUser());
		}
		// We're just checking if the new channel joiner is king, if so op them
		else if(jEvent != null) {
			if(isUserKing(jEvent.getUser())) {
				jEvent.getBot().sendMessage(jEvent.getChannel(), "ALL RISE! KING " + jEvent.getUser().getNick() + " HAS ENTERED THE ROOM!");
				jEvent.getBot().op(jEvent.getChannel(), jEvent.getUser());
			}
		}
	}
	
	// Class constructor for setting a new king
	public KingHandler(MessageEvent event) {
		this.mEvent = event;
	}
	
	// Class constructor for checking if a user is king
	public KingHandler(JoinEvent event) {
		this.jEvent = event;
	}
	
	private void setNewKing(User newKing) {
		// Create a new instance of the database
		database = new Database();
		try {
			// Connect to the database
			database.connect();
			// Execute the query to add the user to the database
			PreparedStatement statement = database.getConnection().prepareStatement("UPDATE Lottery SET Nick = ?, Host = ?, DateCrowned = ?");
			statement.setString(1, newKing.getNick());
			statement.setString(2, newKing.getHostmask());
			statement.setTimestamp(3, new java.sql.Timestamp(new Date().getTime()));
			statement.executeUpdate();
		} catch (Exception ex) {
			IRCUtils.Log(IRCUtils.LOG_ERROR, ex.getMessage());
			ex.printStackTrace();
		}
	}
	
	private boolean isUserKing(User candidate) {
		// Create a new instance of the database
		database = new Database();
		try {
			// Connect to the database
			database.connect();
			// Execute the query to get the current king and check if they are the king
			PreparedStatement statement = database.getConnection().prepareStatement("SELECT * FROM Lottery WHERE Host = ?");
			statement.setString(1, candidate.getHostmask());
			ResultSet resultSet = statement.executeQuery();
			if(resultSet.next()) return true;
			else return false;
		} catch (Exception ex) {
			IRCUtils.Log(IRCUtils.LOG_ERROR, ex.getMessage());
			ex.printStackTrace();
			return false;
		}
	}
	
	public static String getKingsNick() {
		// Create a new instance of the database
		database = new Database();
		try {
			// Connect to the database
			database.connect();
			// Execute the query to get the current king and check if they are the king
			PreparedStatement statement = database.getConnection().prepareStatement("SELECT Nick FROM Lottery");
			ResultSet resultSet = statement.executeQuery();
			if(resultSet.next()) return resultSet.getString("Nick");
			else return null;
		} catch (Exception ex) {
			IRCUtils.Log(IRCUtils.LOG_ERROR, ex.getMessage());
			ex.printStackTrace();
			return null;
		}
	}
}
