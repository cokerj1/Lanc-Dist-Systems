import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Scanner;

import javax.crypto.*;
import java.nio.file.Path; 
import java.nio.file.Paths; 
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;

public class AuctionClient{
     public static void main(String[] args) {

        /** Make the person enter the email 
         *  Check if the if the user's email is in the usermap (DONE)
         *  --> if it is, extract the newUserInfo to challenge/authenticate
         *  --> if it isn't, create new user and create newUserInfo, then challenge/authenticate
         *  Continue with main program after success
         */

        try { // retrieving the registry of the local host by 'auction'
            String name = "Auction";
            Registry registry = LocateRegistry.getRegistry("localhost");
            Auction server = (Auction) registry.lookup(name);
            Integer iID;

            Scanner input = new Scanner(System.in);
            System.out.println("Enter your email: ");
            String emailString = input.nextLine();
            // get new userinfo
            NewUserInfo nui = server.newUser(emailString);
            //retrieving user signature using their private key
            byte[] pvtUserSignature = createSignature(emailString, nui.privateKey);
            Integer uID =nui.userID;

            // authenticating the digital signature of the user
            boolean result1 = server.authenticate(uID, pvtUserSignature);
            //System.out.println("Result 1: " + result1);
            //retrieving server signature & verifying it
            byte[] serverSign = server.challenge(0);
            boolean result2 = serverAuthenticate(serverSign);
            //System.out.println("Result 2: " + result2);

            // Checking that both signature verifications return true before continuing
            if (result1==false || result2==false) {
                System.err.println("Authentication failed.");
                System.exit(0);
            }
            else{
                System.out.println("Authentication successful.");
            }
            
            Boolean end = false;
            int selection;
            String[] options = {"Choose from these choices:",
            "1-- Create new auction",
            "2-- Make a bid", 
            "3-- List auctions", 
            "4-- Close auction", 
            "5-- List individual item",
            "6-- End Client"};

            while (!end) {
                for (String string : options) {
                    System.out.println(string);
                }
                System.out.println("Enter the number of your choice: ");
                try {
                    selection = input.nextInt();
                    System.out.println("\n");
                    switch (selection) {
                        case 1: // Creating a new auction item to place on the lot
                            try {
                                AuctionSaleItem newItem = new AuctionSaleItem();
                                input.nextLine();
                                System.out.println("Enter name of the item to auction: ");
                                newItem.name = input.nextLine();
                                System.out.println("Enter item description: ");
                                newItem.description = input.nextLine();
                                System.out.println("Enter starting item price: ");
                                newItem.reservePrice = input.nextInt();
                                input.nextLine();
                                iID = server.newAuction(uID, newItem);
                                System.out.println("The item ID is:" + iID);
                                System.out.println("Item Name: " + newItem.name);   
                                System.out.println("Item Description: " + newItem.description);   
                                System.out.println("Starting Bid: " + newItem.reservePrice);
                                System.out.println("\n ///////////////////////////////////////// \n");
                            } catch (Exception e) {
                                System.err.println("An error has occured, try again");
                            }
                            break;
                        case 2: // Attempting a bid on an item
                            try {
                                System.out.println("Enter Item ID: ");
                                iID = input.nextInt();
                                System.out.println("Enter bidding price: ");
                                Integer bidPrice = input.nextInt();
                                Boolean makeBid = server.bid(uID, iID, bidPrice);
                                if (makeBid) {
                                    System.out.println("Your bid is now confirmed to have the highest price \n New price: " + bidPrice);
                                } else {
                                    System.err.println("You must enter a bid larger than the highest bid, try again!");
                                }
                            } catch (Exception e) {
                                System.err.println("An error has occured, try again");
                            }
                            break;
                        case 3: // Listing of all the items in the lot
                            AuctionItem[] arry = server.listItems();
                            try {
                                for (AuctionItem item : arry) {
                                    System.out.println("Item ID: " + item.itemID);
                                    System.out.println("Item Name: " + item.name);   
                                    System.out.println("Item Description: " + item.description);   
                                    System.out.println("Highest Bid: " + item.highestBid);
                                    System.out.println("\n ///////////////////////////////////////// \n");
                                }
                            } catch (Exception e) {
                                System.err.println("The lot is empty");
                            }
                            break;
                        case 4: // Closing an auction
                            System.out.println("Enter Item ID: ");
                            iID = input.nextInt();
                            try {
                                AuctionCloseInfo info = server.closeAuction(uID, iID);
                                System.out.println("Email of winning bidder: " + info.winningEmail);
                                System.out.println("Final bidding price: " + info.winningPrice);
                            } catch (Exception e) {
                                System.err.println("An error has occured, ensure you enter a valid userID/itemID pair and you own the auction");
                            }
                            break;
                        case 6: // closing the client down
                            end = true;
                            input.close();
                            break;
                        case 5: // Retrieving the specification of a particular item.
                            System.out.println("Enter Item ID: ");
                            uID= input.nextInt();
                            try {
                                AuctionItem item = server.getSpec(uID);
                                System.out.println("Item ID: " + item.itemID);
                                System.out.println("Item Name: " + item.name);   
                                System.out.println("Item Description: " + item.description);   
                                System.out.println("Highest Bid: " + item.highestBid);
                                System.out.println("\n ///////////////////////////////////////// \n");
                            } catch (Exception e) {
                                System.err.println("Item not found");
                            }
                            break;

                    }
                } catch (Exception e) { // The user has not entered a number
                    System.err.println("Please enter a number...");
                    System.out.println("\n");
                    input.nextLine();
                }
            }
        
        }
        catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }

    //creating private signature of user email
    public static byte[] createSignature(String email, byte[] pvtKey){
        Challenge challenge = new Challenge(pvtKey, email);
        try {
            byte[] signature = challenge.init();
            return signature;
        } catch (Exception e) {
            System.err.println("Error: User not found");
            return null;
        }
    }

    public static boolean serverAuthenticate(byte[] signature){
        Path path = Paths.get("../keys/server_public.key");

        try {
            // Loading server public key from path
            byte[] bytes = Files.readAllBytes(path);
            Authenticate authenticate = new Authenticate(signature, bytes, "auction");
            boolean result = authenticate.init();
            return result;
        } catch (Exception e) {
            System.err.println("Error verifying server signature");
            return false;
        }
    }
}