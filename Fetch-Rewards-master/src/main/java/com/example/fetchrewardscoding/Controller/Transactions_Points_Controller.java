package com.example.fetchrewardscoding.Controller;

import com.example.fetchrewardscoding.Entity.Transactions;
import com.example.fetchrewardscoding.Entity.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class Transactions_Points_Controller {

    Logger logger = LoggerFactory.getLogger(Transactions_Points_Controller.class);

    private static int counter = 0;
    private static final Map<Integer, Users> users = new HashMap<>();

    //Retrieve all the transactions
    @GetMapping("/transactions")
    public Map<Integer, PriorityQueue<Transactions>> getUsersTransactions() {
        Map<Integer, PriorityQueue<Transactions>> usersTransactions = new HashMap<>();

        users.entrySet().forEach((e)-> usersTransactions.put(e.getKey(),e.getValue().getTransactionsQueue()));
        return usersTransactions;
    }

    //Retrieve each user transaction by userId
    @GetMapping("/transactions/{id}")
    public ResponseEntity<PriorityQueue<Transactions>> getTransactionById(@Validated @PathVariable(name = "id") int id){
        if(users.containsKey(id)){
            PriorityQueue<Transactions> transactionEachUser = users.get(id).getTransactionsQueue();
            return ResponseEntity.ok().body(transactionEachUser);
        }
        else {
            throw new RuntimeException("Invalid User Id!!");
        }
    }

    //Post request to add points for each transactions
    @PostMapping("/addPoints/{id}")
    public ResponseEntity<Transactions> addPoints(@Validated @PathVariable int id, @Validated @RequestBody Transactions transactions){

        Users user;

        counter++;

        transactions.setTransactionId(counter);

        transactions.setTimestamp(LocalDateTime.now());

        String payer = transactions.getPayerName();

        long point = transactions.getPoints();

        if(!users.containsKey(id)){
            user = new Users(id);
            users.put(user.getUserId(), user);
        }
        else {
            user = users.get(id);
        }

        long totalPoints = user.getTotalrewardPoints();

        Map<String, Long> pointsPerPayer = user.getPointsPerPayer();

        PriorityQueue<Transactions> transactionsQ = user.getTransactionsQueue();

        if(point>0) transactionsQ.offer(transactions);

        else if(point<0) {
            if (!pointsPerPayer.containsKey(payer))
                throw new RuntimeException("Invalid transaction record");
            else {

                Transactions currentTransaction = transactionsQ.stream()
                        .filter(t -> t.getPayerName().equals(transactions.getPayerName())).findFirst().orElse(null);

                if ((pointsPerPayer.get(payer) + point) > 0)
                    currentTransaction.setPoints(pointsPerPayer.get(payer) + point);

                else if ((pointsPerPayer.get(payer) + point) == 0) transactionsQ.remove(currentTransaction);

                else throw new RuntimeException("Invalid transaction record");

            }
        }

        //Update the user total points
        user.setTotalrewardPoints(user.getTotalrewardPoints() + point);

        //Update Points for each Payer
        pointsPerPayer.put(transactions.getPayerName(),(pointsPerPayer.getOrDefault(transactions.getPayerName(), 0L).longValue() + point));


        return ResponseEntity.ok().body(transactions);
    }


    //Subtract spend points
    @GetMapping("/spend/{spend}/{id}")
    public ResponseEntity<List<StringBuilder>> spendPoints(@Validated @PathVariable int id, @Validated @PathVariable long spend){

        if(!users.containsKey(id))
            throw new RuntimeException("User not found or absent!");

        //List of spend points
        List<StringBuilder> spentPerPayer = new ArrayList<>();

        Users user = users.get(id);
        Map<String, Long> pointsPerPayer = user.getPointsPerPayer();

        PriorityQueue<Transactions> transactionsQ = user.getTransactionsQueue();

        long totalPoints = user.getTotalrewardPoints();
        logger.info("Initial Total Points Before Spending for user " + id + " : " + totalPoints);

        logger.info("Actual Spend Points:" + spend);

        if(spend > user.getTotalrewardPoints()) throw new RuntimeException("Unsufficient Reward Balance!!!");

        while(spend > 0 && !transactionsQ.isEmpty()){
            long remaining;
            Transactions currTransaction  = transactionsQ.poll();

            if(currTransaction.getPoints()<=spend)
                remaining = currTransaction.getPoints();
            else remaining = spend;

            spentPerPayer.add(new StringBuilder(currTransaction.getPayerName()).append(" -"+(remaining)));




            user.setTotalrewardPoints(user.getTotalrewardPoints() - remaining);


            pointsPerPayer.put(currTransaction.getPayerName(),(pointsPerPayer.getOrDefault(currTransaction.getPayerName(), 0L).longValue() - remaining));

            logger.info("Spent Per Payer List:" + spentPerPayer);
            spend -= remaining;
            logger.info("Spend Points after deducting from "+currTransaction.getPayerName() + " : " + spend);

            logger.info("Total Points for the user" +" "+id+ " : "+user.getTotalrewardPoints());
        }
        return ResponseEntity.ok().body(spentPerPayer);
    }

    //Show the balance for each user
    @GetMapping("/balance/{id}")
    public ResponseEntity<List<StringBuilder>> balanceOfUserForEachPayer(@Validated @PathVariable int id){
        //List of Balanced Points for with given userId
        List<StringBuilder> balancePoint = new ArrayList<>();

        if(!users.containsKey(id)) throw new RuntimeException("User not found or absent!");

        Users user = users.get(id);

        user.getPointsPerPayer().entrySet().forEach(e-> balancePoint.add(new StringBuilder(e.getKey()+" : "+e.getValue())));

        return ResponseEntity.ok().body(balancePoint);
    }
}
