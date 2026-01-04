package cn.autoai.demo.console;

/**
 * Person query parameter class
 */
public class PersonQuery {
    /** Name */
    private String name;
    private String department;

    public PersonQuery() {
        // No-argument constructor for JSON deserialization
    }
    
    public PersonQuery(String name, String department) {
        this.name = name;
        this.department = department;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDepartment() {
        return department;
    }
    
    public void setDepartment(String department) {
        this.department = department;
    }
    
    @Override
    public String toString() {
        return "PersonQuery{name='" + name + "', department='" + department + "'}";
    }
}