package org.nit.collections;

/**
 * CustomCollection is a simple implementation of a collection that can hold both homogeneous and heterogeneous data types.
 * It provides basic functionalities to add, remove, display, search, and update elements in the collection.
 *
 * @author Vijay Kumar,Venkey
 * @version 1.0
 * @since 2025-09-19
 */







public class CustomCollection {
    //initializing
    Object[] customArray;
    int size = 10;
    int index = 0;

    //constructor
    public CustomCollection() {
        customArray = new Object[size];
    }

    //creating helper methods to add, remove, display, search and update elements in the array
    /*
      Adds an element to the collection.

      @param obj The object to be added to the collection.
     */


    //Add Elements to the array
    public void add(Object object) {
        //checking if index is less than size of array
        if (index == customArray.length) {
            grow();
        }
        customArray[index] = object;
        index++;
    }


    //Grows the size of the collection by doubling its current size.
    private void grow() {
        int newSize = customArray.length * 2;
        Object[] newArray = new Object[newSize];
        //copying elements from old array to new array
        for (int i = 0; i < customArray.length; i++) {
            newArray[i] = customArray[i];
        }
        customArray = newArray;
    }

    //Search an element in the array
    public void search(Object object) {
        for (int i = 0; i < customArray.length; i++) {
            //checking if element is found
            try {
                if (customArray[i] == null) {
                    continue;
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

            if (customArray[i].equals(object)) {
                System.out.println("Element found at index: " + i);

                return ;
            }



        }
        System.out.println("Element not found");
    }
    //Remove element in the array by index
    public void remove (int index){
        if (index<0 || index>=customArray.length ){
            System.out.println("Index out of bounds");
            return;
        }
        customArray[index]=null;
        System.out.println("Element removed at index: "+index);
        //shifting elements to the left
        for (int i=index;i<customArray.length-1;i++) {
            customArray[i] = customArray[i + 1];
        }

    }
  //update an element in the array
    public void update(int index, Object newValue) {
    	//checking if index is valid or not
    	if(index<0 || index>=customArray.length) {
    		throw new ArrayIndexOutOfBoundsException("Index out of bounds");
    	}else {
    		customArray[index]=newValue;
    	}
//Helper method to display elements in the array
    public void display() {
        try {
            // Check if array is null or empty
            if (customArray == null || customArray.length == 0) {
                System.out.println("Array is empty");
            } else {
                System.out.print("[");
                for (int i = 0; i < customArray.length; i++) {
                    System.out.print(customArray[i]);
                    if (i < customArray.length - 1) {
                        System.out.print(", "); 
                    }
                }
                System.out.println("]");
            }
        } catch (Exception e) {
            System.out.println("An error occurred while displaying the array: " + e.getMessage());
        }
    }

}