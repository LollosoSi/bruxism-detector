package bruxism_grapher2;

import java.util.ArrayList;

public class StatData implements Comparable<StatData> {

	ArrayList<String> dataNames = new ArrayList<>();
	ArrayList<String> dataValues = new ArrayList<>();
	ArrayList<String> infoNames = new ArrayList<>();

	public void addData(String name, String value) {
		dataNames.add(name);
		dataValues.add(value);
	}

	public void addInfo(String name) {
		infoNames.add(name);
	}
	public StatData(){
	}

	public String produce_csv_header(){
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for(String name : dataNames){
			if(first){

				first=false;
			}else{
				sb.append(";");
			}
			sb.append(name);
		}

		sb.append(";Info");

		return sb.toString();
	}

	String produce_info_line(){
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for(String name : infoNames) {
			if(first){
				first=false;
			}else{
				sb.append(",");
			}
			sb.append(name);
		}
		return sb.toString();
	}
	public String produce_csv_line () {
		StringBuilder sb = new StringBuilder();

		boolean first = true;
		for(String value : dataValues){
			if(first){

				first=false;
			}else{
				sb.append(";");
			}
			sb.append(value);
		}
		sb.append(";");
		sb.append(produce_info_line());
		return sb.toString();
	}

	public String getSessionName() {
		return getItem("Date");
	}

	public String getItem(String name){
		return dataValues.get(dataNames.indexOf(name));
	}

	@Override
    public int compareTo(StatData other) {
        return this.getSessionName().compareTo(other.getSessionName());
    }
	
}
