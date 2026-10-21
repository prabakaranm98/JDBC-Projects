package JDBC;

import java.sql.*;

public class practice_jdbc {

	public static void main(String[] args) {
		
		try
		{
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/JDBC", "root", "praba");
			System.out.println("Connected...");
			con.close();
 
 
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
	}

}
