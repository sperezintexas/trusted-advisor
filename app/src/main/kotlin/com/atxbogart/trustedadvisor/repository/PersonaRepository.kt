package com.atxbogart.trustedadvisor.repository

import com.atxbogart.trustedadvisor.model.Persona
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface PersonaRepository : MongoRepository<Persona, String> {
}