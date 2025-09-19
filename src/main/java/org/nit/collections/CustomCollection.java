package org.nit.collections;

/**
 * CustomCollection is a simple implementation of a collection that can hold both homogeneous and heterogeneous data types.
 * It provides basic functionalities to add, remove, display, search, and update elements in the collection.
 *
 * @author Vijay Kumar
 * @version 1.0
 * @since 2025-09-19
 */
public class CustomCollection {

    //creating array that can hold homogeneous and heterogeneous data
    Object [] customArray;
    //initializing size of array
    int size = 10;
    //creating array in constructor
   public CustomCollection(){
        customArray = new Object[size];
    }
    //initializing index
    int index = 0;

    //creating helper methods to add, remove, display, search and update elements in the array
    /*
      Adds an element to the collection.

      @param obj The object to be added to the collection.
     */
    public void add(Object object){
        //checking if index is less than size of array
        if(index==customArray.length){
            grow();
        }
        customArray[index]=object;
        index++;
    }
    /*
      Grows the size of the collection by doubling its current size.
     */
    private void grow() {
        int newSize= customArray.length*2;
        Object [] newArray = new Object[newSize];
        //copying elements from old array to new array
        for (int i=0; i<customArray.length; i++){
            newArray[i]=customArray[i];
        }
        customArray = newArray;
    }

}