package com.atxbogart.trustedadvisor.controller

import com.atxbogart.trustedadvisor.model.Persona
import com.atxbogart.trustedadvisor.service.PersonaService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/personas")
@CrossOrigin(origins = ["http://localhost:3000"])
class PersonaController(private val service: PersonaService) {

    @GetMapping
    fun list(): List<Persona> = service.findAll()

    @PostMapping
    fun create(@RequestBody persona: Persona): Persona = service.save(persona)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Persona?> = 
        service.findById(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody persona: Persona): ResponseEntity<Persona> = 
        service.findById(id)?.let { 
            val updated = persona.copy(id = id)
            ResponseEntity.ok(service.save(updated))
        } ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        service.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}