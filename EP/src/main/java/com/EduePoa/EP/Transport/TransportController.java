package com.EduePoa.EP.Transport;

import com.EduePoa.EP.FinanceTransaction.Request.CreateTransactionDTO;
import com.EduePoa.EP.Transport.AssignTransport.Request.AssignTransportRequestDTO;
import com.EduePoa.EP.Transport.Request.TransportRequestDTO;
import com.EduePoa.EP.Transport.TransportTransactions.Requests.TransportTransactionRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/transport/vehicle")
@RequiredArgsConstructor
public class TransportController {
    private final TransportService transportService;

    @PostMapping("/create")
    ResponseEntity<?> create(@RequestBody TransportRequestDTO transportRequestDTO){
        var response = transportService.create(transportRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("{id}")
    ResponseEntity<?> getById(@PathVariable Long id){
        var response = transportService.getById(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("all")
    ResponseEntity<?> getAll(){
        var response = transportService.getAll();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PutMapping("update/{id}")
    ResponseEntity<?> update(@PathVariable Long id, @RequestBody TransportRequestDTO dto){
        var response = transportService.update(id, dto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @DeleteMapping("delete/{id}")
    ResponseEntity<?> delete(@PathVariable Long id){
        var response = transportService.delete(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("assign")
    ResponseEntity<?> assign(@RequestBody AssignTransportRequestDTO request){
        var response = transportService.assign(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("assignments")
    ResponseEntity<?> assignments(){
        var response = transportService.assignments();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @DeleteMapping("delete-assignments/{id}")
    ResponseEntity<?> deleteAssignments(@PathVariable Long  id){
        var response = transportService.deleteAssignments(id);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("students-transport")
    ResponseEntity<?> studentTransport(){
        var response = transportService.studentTransport();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @PostMapping("create-transport-transaction/{studentId}")
    ResponseEntity<?> createTransportTransaction(@PathVariable Long studentId,@RequestBody TransportTransactionRequestDTO transportTransactionRequestDTO){
        var response = transportService.createTransportTransaction(studentId,transportTransactionRequestDTO);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
    @GetMapping("transactions/all")
    ResponseEntity<?> getAllTransportTransactions(){
        var response = transportService.getAllTransportTransactions();
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }




}
