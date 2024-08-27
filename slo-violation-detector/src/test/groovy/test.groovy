class Car{
    String model;
    String year;
    Car(String model,String year){
        this.model = model;
        this.year = year;
    }

    @Override
    String toString() {
        return "model: "+model+" year: "+year
    }
}

HashMap <String,Car> testmap = new HashMap();
mycar = new Car("Honda","2006");
nextcar = new Car("Kia","2020");
testmap.put("mycar",mycar);
testmap.put("nextcar",nextcar);
print("Original value "+mycar)
mycar.year = "2016"
print("Updated value "+testmap.get("mycar"))